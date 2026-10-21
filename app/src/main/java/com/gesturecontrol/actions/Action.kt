package com.gesturecontrol.actions

import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

/** Something the phone does in response to a gesture. */
interface Action {
    fun perform()
}

/**
 * Sends a media key to the active media session (Spotify, YouTube Music, ...).
 * Needs no special permissions.
 */
class MediaKeyAction(private val audioManager: AudioManager, private val keyCode: Int) : Action {
    override fun perform() {
        val now = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    override fun toString() = "MediaKeyAction(${KeyEvent.keyCodeToString(keyCode)})"
}
