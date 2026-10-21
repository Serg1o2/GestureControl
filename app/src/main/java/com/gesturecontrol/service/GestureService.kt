package com.gesturecontrol.service

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.gesturecontrol.accessibility.ForegroundAppTracker
import com.gesturecontrol.actions.ActionDispatcher
import com.gesturecontrol.camera.CameraController
import com.gesturecontrol.data.InstalledAppsRepository
import com.gesturecontrol.data.SelectedAppsRepository
import com.gesturecontrol.data.SettingsRepository
import com.gesturecontrol.gestures.GestureConfig
import com.gesturecontrol.gestures.GestureEngine
import com.gesturecontrol.gestures.PowerSaveConfig
import com.gesturecontrol.gestures.PowerSaveThrottler
import com.gesturecontrol.ui.hasPermission
import com.gesturecontrol.vision.HandAnalyzer
import com.gesturecontrol.vision.HandLandmarkerEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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

    private lateinit var camera: CameraController
    private var landmarker: HandLandmarkerEngine? = null
    private var gateJob: Job? = null

    /** Power-save setting the camera is currently bound with; null when unbound. */
    private var boundWithPowerSave: Boolean? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) camera.setTargetRotation(displayRotation())
        }
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
    }

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
        if (gateJob == null) gateJob = lifecycleScope.launch { observeGate() }
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

    private suspend fun observeGate() {
        combine(
            serviceState.debugPreviewActive,
            screenOnFlow(this),
            foregroundApps.foregroundPackage,
            selectedApps.selectedPackages,
            settings.powerSaveEnabled,
        ) { debug, screenOn, foreground, selected, powerSave ->
            val status = when {
                debug -> ServiceStatus.PausedDebug
                !screenOn -> ServiceStatus.ScreenOff
                foreground != null && foreground in selected ->
                    ServiceStatus.Active(foreground, installedApps.labelOf(foreground))
                else -> ServiceStatus.Waiting
            }
            status to powerSave
        }.distinctUntilChanged().collect { (status, powerSave) -> apply(status, powerSave) }
    }

    private suspend fun apply(status: ServiceStatus, powerSave: Boolean) {
        serviceState.status.value = status
        getSystemService(NotificationManager::class.java).notify(
            GestureNotifications.NOTIFICATION_ID,
            GestureNotifications.build(this, GestureNotifications.statusText(this, status)),
        )
        if (status is ServiceStatus.Active) {
            if (boundWithPowerSave != powerSave) bindCamera(powerSave)
        } else {
            unbindCamera()
        }
    }

    private suspend fun bindCamera(powerSave: Boolean) {
        val engine = landmarker ?: withContext(Dispatchers.Default) { HandLandmarkerEngine(this@GestureService) }
            .also { landmarker = it }
        val analyzer = HandAnalyzer(
            landmarker = engine,
            gestureEngine = GestureEngine(GestureConfig()),
            throttler = PowerSaveThrottler(PowerSaveConfig(enabled = powerSave)),
        ) { result ->
            result.event?.let { actionDispatcher.dispatch(it.gesture) }
        }
        try {
            camera.bind(this, analyzer, displayRotation())
            boundWithPowerSave = powerSave
        } catch (e: Exception) {
            Log.e(TAG, "Camera bind failed", e)
            boundWithPowerSave = null
        }
    }

    private fun unbindCamera() {
        camera.unbind()
        boundWithPowerSave = null
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
