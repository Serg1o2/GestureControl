package com.gesturecontrol.actions

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.gesturecontrol.R
import com.gesturecontrol.accessibility.PointerBridge
import com.gesturecontrol.gestures.custom.ActionSpec
import com.gesturecontrol.gestures.custom.Direction
import com.gesturecontrol.gestures.mapping.CommandPlanner
import com.gesturecontrol.gestures.mapping.GlobalActionKind
import com.gesturecontrol.gestures.mapping.TouchCommand
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes the action library of custom gestures. Safe to call from any thread; failures show a
 * short toast instead of throwing.
 */
@Singleton
class ActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaDispatcher: ActionDispatcher,
    private val bridge: PointerBridge,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var torchOn = false

    init {
        // Track the real torch state (other apps may change it too).
        runCatching {
            cameraManager.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == torchCameraId) torchOn = enabled
                }
            }, main)
        }
    }

    private val torchCameraId: String? by lazy {
        runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                val c = cameraManager.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        }.getOrNull()
    }

    fun execute(spec: ActionSpec) {
        val ok = try {
            when (spec) {
                is ActionSpec.Media -> mediaDispatcher.dispatchMedia(spec.kind).let { true }
                is ActionSpec.Volume -> {
                    val dir = if (spec.direction == Direction.UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
                    true
                }
                is ActionSpec.Global -> bridge.send(TouchCommand.Global(spec.kind))
                ActionSpec.Screenshot -> bridge.send(TouchCommand.Global(GlobalActionKind.SCREENSHOT))
                is ActionSpec.TrackSwipe -> bridge.send(CommandPlanner.forTrackSwipe(spec.toLeft))
                is ActionSpec.LaunchApp -> launch(spec.packageName)
                ActionSpec.Flashlight -> toggleTorch()
                is ActionSpec.Brightness -> adjustBrightness(spec.direction)
                is ActionSpec.TaskerIntent -> {
                    context.sendBroadcast(Intent(spec.action))
                    true
                }
                ActionSpec.None -> true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Action $spec failed", e)
            false
        }
        if (!ok) main.post { Toast.makeText(context, R.string.action_failed, Toast.LENGTH_SHORT).show() }
    }

    /** Background activity starts are allowed while our accessibility service is bound. */
    private fun launch(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    private fun toggleTorch(): Boolean {
        val id = torchCameraId ?: return false
        cameraManager.setTorchMode(id, !torchOn)
        return true
    }

    /** Needs the "modify system settings" permission (Settings.System.canWrite). */
    private fun adjustBrightness(direction: Direction): Boolean {
        if (!Settings.System.canWrite(context)) return false
        val resolver = context.contentResolver
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        val next = (current + if (direction == Direction.UP) BRIGHTNESS_STEP else -BRIGHTNESS_STEP).coerceIn(5, 255)
        return Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, next)
    }

    private companion object {
        const val TAG = "ActionExecutor"
        const val BRIGHTNESS_STEP = 40
    }
}
