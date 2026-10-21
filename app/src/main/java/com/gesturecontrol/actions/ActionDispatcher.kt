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
    )

    fun dispatch(gesture: Gesture) {
        val action = bindings[gesture] ?: return
        Log.d(TAG, "$gesture -> $action")
        action.perform()
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
