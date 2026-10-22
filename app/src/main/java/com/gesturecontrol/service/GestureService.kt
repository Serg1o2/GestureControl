package com.gesturecontrol.service

import com.gesturecontrol.ui.AppLanguage
import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.widget.Toast
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.gesturecontrol.R
import com.gesturecontrol.accessibility.ForegroundAppTracker
import com.gesturecontrol.accessibility.OverlayState
import com.gesturecontrol.accessibility.PointerBridge
import com.gesturecontrol.actions.ActionDispatcher
import com.gesturecontrol.camera.CameraController
import com.gesturecontrol.data.InstalledAppsRepository
import com.gesturecontrol.data.SelectedAppsRepository
import com.gesturecontrol.data.SettingsRepository
import com.gesturecontrol.data.ZoneRepository
import com.gesturecontrol.data.CustomGestureRepository
import com.gesturecontrol.data.toTemplate
import com.gesturecontrol.actions.ActionExecutor
import com.gesturecontrol.gestures.custom.ActionSpec
import com.gesturecontrol.gestures.custom.CustomGestureRecognizer
import com.gesturecontrol.gestures.EngineState
import com.gesturecontrol.gestures.Gesture
import com.gesturecontrol.gestures.GestureConfig
import com.gesturecontrol.gestures.GestureToggles
import com.gesturecontrol.gestures.Sensitivity
import com.gesturecontrol.gestures.SensitivityMapping
import com.gesturecontrol.gestures.GestureEngine
import com.gesturecontrol.gestures.PowerSaveConfig
import com.gesturecontrol.gestures.PowerSaveThrottler
import com.gesturecontrol.gestures.mapping.CommandPlanner
import com.gesturecontrol.gestures.mapping.ScreenMapper
import com.gesturecontrol.gestures.mapping.TouchCommand
import com.gesturecontrol.gestures.mapping.ZoneSet
import com.gesturecontrol.gestures.pointer.PointerConfig
import com.gesturecontrol.gestures.pointer.PointerEngine
import com.gesturecontrol.gestures.pointer.PointerEvent
import com.gesturecontrol.gestures.pointer.PressMode
import com.gesturecontrol.ui.hasPermission
import com.gesturecontrol.vision.AnalysisResult
import com.gesturecontrol.vision.HandAnalyzer
import com.gesturecontrol.vision.HandLandmarkerEngine
import com.gesturecontrol.vision.ClassifierKind
import com.gesturecontrol.vision.PoseClassifierFactory
import com.gesturecontrol.vision.PoseClassifiers
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Foreground service (type camera) that runs gesture recognition.
 *
 * Android 14+ only allows starting a camera foreground service while the app is visible,
 * so the service is started from the main screen and then stays alive. It binds the camera
 * only while a selected app is on screen (and the screen is on), and releases it otherwise.
 */
@AndroidEntryPoint
class GestureService : LifecycleService() {

    @Inject lateinit var serviceState: GestureServiceState
    @Inject lateinit var foregroundApps: ForegroundAppTracker
    @Inject lateinit var selectedApps: SelectedAppsRepository
    @Inject lateinit var installedApps: InstalledAppsRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var actionDispatcher: ActionDispatcher
    @Inject lateinit var zones: ZoneRepository
    @Inject lateinit var bridge: PointerBridge
    @Inject lateinit var classifierFactory: PoseClassifierFactory
    @Inject lateinit var customGestures: CustomGestureRepository
    @Inject lateinit var actionExecutor: ActionExecutor

    /** Custom gestures of the app on screen (plus global); read on the analysis thread. */
    private val customRecognizer = CustomGestureRecognizer(emptyList())
    @Volatile private var customActions: Map<Long, ActionSpec> = emptyMap()

    /** Classifiers of the current binding; closed on the analysis thread after unbind. */
    private var classifiers: PoseClassifiers? = null

    private lateinit var camera: CameraController
    private val mainHandler = Handler(Looper.getMainLooper())
    private var landmarker: HandLandmarkerEngine? = null
    private var gateJob: Job? = null

    /** Settings the camera is currently bound with; null when unbound. */
    private var boundWith: BindKey? = null

    /** Read on the analysis thread, written on the main thread. */
    @Volatile private var pointerEngine: PointerEngine? = null
    @Volatile private var currentZones: ZoneSet = ZoneSet.EMPTY
    @Volatile private var currentMapper: ScreenMapper = ScreenMapper.fromRegion()
    @Volatile private var gestureEngine: GestureEngine? = null

    /** The app on screen uses the cursor (cursor enabled and the app has zones). */
    @Volatile private var cursorApp = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) camera.setTargetRotation(displayRotation())
        }
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    override fun onCreate() {
        super.onCreate()
        camera = CameraController(this)
        GestureNotifications.ensureChannel(this)
        getSystemService(DisplayManager::class.java)
            .registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!startInForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (gateJob == null) {
            gateJob = lifecycleScope.launch { observeGate() }
            lifecycleScope.launch { settings.volumeOnlyWhenPlaying.collect { actionDispatcher.volumeOnlyWhenPlaying = it } }
            lifecycleScope.launch { observePointerInputs() }
            lifecycleScope.launch { observeCustomGestures() }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(): Boolean {
        if (!hasPermission(Manifest.permission.CAMERA)) return false
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
        val notification = GestureNotifications.build(this, GestureNotifications.statusText(this, ServiceStatus.Waiting))
        return try {
            ServiceCompat.startForeground(this, GestureNotifications.NOTIFICATION_ID, notification, type)
            if (serviceState.status.value == ServiceStatus.Off) serviceState.status.value = ServiceStatus.Waiting
            true
        } catch (e: Exception) {
            // E.g. ForegroundServiceStartNotAllowedException when started from the background.
            Log.e(TAG, "Cannot start foreground service", e)
            false
        }
    }

    /** Settings that require a new analyzer; calibration and zones are updated live instead. */
    private data class BindKey(
        val powerSave: Boolean,
        val cursorEnabled: Boolean,
        val pressMode: PressMode,
        val deadZone: Float,
        val classifier: ClassifierKind,
        val sensitivity: Sensitivity,
        val toggles: GestureToggles,
    )

    private suspend fun observeGate() {
        combine(
            serviceState.debugPreviewActive,
            screenOnFlow(this),
            foregroundApps.foregroundPackage,
            selectedApps.selectedPackages,
            combine(
                settings.powerSaveEnabled,
                settings.pointerSettings,
                settings.classifierKind,
                settings.sensitivity,
                settings.gestureToggles,
            ) { powerSave, pointer, kind, sensitivity, toggles ->
                BindKey(powerSave, pointer.enabled, pointer.pressMode, pointer.deadZone, kind, sensitivity, toggles)
            },
        ) { debug, screenOn, foreground, selected, key ->
            val status = when {
                debug -> ServiceStatus.PausedDebug
                !screenOn -> ServiceStatus.ScreenOff
                foreground != null && foreground in selected ->
                    ServiceStatus.Active(foreground, installedApps.labelOf(foreground))
                else -> ServiceStatus.Waiting
            }
            status to key
        }.distinctUntilChanged().collect { (status, key) -> apply(status, key) }
    }

    /** Custom gestures of the app on screen plus the global profile, parsed off the main thread. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun observeCustomGestures() {
        foregroundApps.foregroundPackage
            .flatMapLatest { pkg -> customGestures.activeFor(pkg) }
            .collect { entities ->
                val parsed = withContext(Dispatchers.Default) {
                    entities.mapNotNull { e -> runCatching { e.toTemplate() to ActionSpec.decode(e.action) }.getOrNull() }
                }
                customActions = parsed.associate { (t, a) -> t.id to a }
                customRecognizer.templates = parsed.map { it.first }
            }
    }

    /** Keeps the pointer engine's calibration and the zones of the app on screen up to date. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun observePointerInputs() {
        combine(
            foregroundApps.foregroundPackage.flatMapLatest { pkg ->
                if (pkg == null) flowOf(emptyList()) else zones.zonesFor(pkg)
            },
            settings.pointerSettings,
        ) { zoneList, pointer -> Triple(ZoneSet(zoneList), pointer.mapper, pointer.enabled) }
            .collect { (zoneSet, mapper, cursorEnabled) ->
                currentZones = zoneSet
                currentMapper = mapper
                cursorApp = cursorEnabled && zoneSet.zones.isNotEmpty()
                applyModeFlags()
            }
    }

    /**
     * Apps with zones use the pointing finger for the cursor; apps without zones use it for
     * finger flicks (track switching), so the two never compete for the same pose.
     */
    private fun applyModeFlags() {
        pointerEngine?.let {
            it.zones = currentZones
            it.mapper = currentMapper
            it.active = cursorApp
        }
        gestureEngine?.fingerSwipesEnabled = !cursorApp
        // In apps with zones the pinch is the cursor's press.
        gestureEngine?.volumeSliderEnabled = !cursorApp
        if (!cursorApp) bridge.overlay.value = OverlayState()
    }

    private suspend fun apply(status: ServiceStatus, key: BindKey) {
        serviceState.status.value = status
        getSystemService(NotificationManager::class.java).notify(
            GestureNotifications.NOTIFICATION_ID,
            GestureNotifications.build(this, GestureNotifications.statusText(this, status)),
        )
        if (status is ServiceStatus.Active) {
            if (boundWith != key) bindCamera(key)
        } else {
            unbindCamera()
        }
    }

    private suspend fun bindCamera(key: BindKey) {
        val engine = landmarker ?: withContext(Dispatchers.Default) { HandLandmarkerEngine(this@GestureService) }
            .also { landmarker = it }
        classifiers?.let { old -> camera.runOnAnalysisThread { old.close() } }
        val poseClassifiers = withContext(Dispatchers.Default) { classifierFactory.create(key.classifier) }
        classifiers = poseClassifiers
        val pointer = if (key.cursorEnabled) {
            PointerEngine(
                key.toggles.applyTo(
                    SensitivityMapping.apply(PointerConfig(pressMode = key.pressMode, deadZone = key.deadZone), key.sensitivity),
                ),
                screenAspect(),
                poseClassifiers.forPointer,
            ).apply {
                zones = currentZones
                mapper = currentMapper
            }
        } else {
            null
        }
        pointerEngine = pointer
        val gestures = GestureEngine(
            key.toggles.applyTo(SensitivityMapping.apply(GestureConfig(), key.sensitivity)),
            poseClassifiers.forGestures,
        )
        gestureEngine = gestures
        applyModeFlags()
        val analyzer = HandAnalyzer(
            landmarker = engine,
            gestureEngine = gestures,
            throttler = PowerSaveThrottler(PowerSaveConfig(enabled = key.powerSave)),
            pointerEngine = pointer,
            onResult = ::onAnalysisResult,
        )
        try {
            camera.bind(this, analyzer, displayRotation())
            boundWith = key
        } catch (e: Exception) {
            Log.e(TAG, "Camera bind failed", e)
            boundWith = null
        }
    }

    /** Called on the analysis thread. */
    private fun onAnalysisResult(result: AnalysisResult) {
        // Custom gestures do not see the hand while it drives the volume slider.
        if (customRecognizer.templates.isNotEmpty() && result.state !is EngineState.VolumeSliding) {
            customRecognizer.process(result.frame)?.let { match ->
                customActions[match.template.id]?.let(actionExecutor::execute)
            }
        }
        result.event?.let { event ->
            when (event.gesture) {
                Gesture.FINGER_LEFT, Gesture.FINGER_RIGHT -> {
                    val swipe = CommandPlanner.forTrackSwipe(toLeft = event.gesture == Gesture.FINGER_LEFT)
                    if (!bridge.send(swipe)) mainHandler.post { warnAccessibilityOff() }
                }
                else -> actionDispatcher.dispatch(event.gesture)
            }
        }
        val pointer = result.pointer ?: return
        // A frame still in flight after unbind (or in an app without zones) must not show the cursor.
        if (pointerEngine == null || !cursorApp) return
        bridge.overlay.value = OverlayState(
            cursor = pointer.cursor,
            pinchClosed = pointer.pinchClosed,
            dwellProgress = pointer.dwellProgress,
            hoveredZone = pointer.hoveredZone?.rect,
        )
        val command = when (val event = pointer.event) {
            is PointerEvent.Press -> CommandPlanner.forPress(event.at, event.zone)
            is PointerEvent.Scroll -> CommandPlanner.forScroll(event.direction)
            null -> return
        }
        if (command is TouchCommand.Media) {
            actionDispatcher.dispatchMedia(command.kind)
        } else if (!bridge.send(command)) {
            mainHandler.post { warnAccessibilityOff() }
        }
    }

    private var lastAccessibilityWarningMs = 0L

    private fun warnAccessibilityOff() {
        val now = SystemClock.uptimeMillis()
        if (now - lastAccessibilityWarningMs < 5000) return
        lastAccessibilityWarningMs = now
        Toast.makeText(this, R.string.hint_accessibility_off, Toast.LENGTH_SHORT).show()
    }

    private fun unbindCamera() {
        camera.unbind()
        classifiers?.let { old -> camera.runOnAnalysisThread { old.close() } }
        classifiers = null
        boundWith = null
        pointerEngine = null
        gestureEngine = null
        bridge.overlay.value = OverlayState()
    }

    @Suppress("DEPRECATION")
    private fun screenAspect(): Float {
        val p = Point()
        getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.getRealSize(p)
        return if (p.x > 0) p.y.toFloat() / p.x else 2f
    }

    private fun displayRotation(): Int =
        getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0

    override fun onDestroy() {
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        unbindCamera()
        val engine = landmarker
        landmarker = null
        camera.runOnAnalysisThread { engine?.close() }
        camera.shutdown()
        serviceState.status.value = ServiceStatus.Off
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GestureService"
        const val ACTION_START = "com.gesturecontrol.action.START"
        const val ACTION_STOP = "com.gesturecontrol.action.STOP"

        /** Must be called while the app is visible (Android 14+ camera FGS restriction). */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, GestureService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GestureService::class.java))
        }
    }
}
