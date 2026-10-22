package com.gesturecontrol.actions

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import com.gesturecontrol.gestures.Gesture
import com.gesturecontrol.gestures.mapping.MediaKind
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Maps gestures to actions. Stage 1 uses a fixed mapping. */
@Singleton
class ActionDispatcher @Inject constructor(@ApplicationContext context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val bindings: Map<Gesture, Action> = mapOf(
        Gesture.SWIPE_RIGHT to MediaKeyAction(audioManager, KeyEvent.KEYCODE_MEDIA_NEXT),
        Gesture.SWIPE_LEFT to MediaKeyAction(audioManager, KeyEvent.KEYCODE_MEDIA_PREVIOUS),
        Gesture.OPEN_PALM_HOLD to MediaKeyAction(audioManager, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
        Gesture.FIST_OPEN to MediaKeyAction(audioManager, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
        // FINGER_LEFT / FINGER_RIGHT are touch swipes on the screen, performed by the gesture service.
    )

    /** Volume steps of the pinch slider are ignored while no music plays (setting, on by default). */
    @Volatile
    var volumeOnlyWhenPlaying: Boolean = true

    fun dispatch(gesture: Gesture) {
        if (gesture == Gesture.VOLUME_UP || gesture == Gesture.VOLUME_DOWN) {
            adjustVolume(up = gesture == Gesture.VOLUME_UP)
            return
        }
        val action = bindings[gesture] ?: return
        Log.d(TAG, "$gesture -> $action")
        action.perform()
    }

    /** One step of the music volume, with the system volume panel as feedback. */
    private fun adjustVolume(up: Boolean) {
        if (volumeOnlyWhenPlaying && !audioManager.isMusicActive) {
            Log.d(TAG, "volume step ignored: no music playing")
            return
        }
        val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    /** Media action assigned to a screen zone. */
    fun dispatchMedia(kind: MediaKind) {
        val keyCode = when (kind) {
            MediaKind.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaKind.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            MediaKind.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        MediaKeyAction(audioManager, keyCode).perform()
    }

    private companion object {
        const val TAG = "ActionDispatcher"
    }
}
