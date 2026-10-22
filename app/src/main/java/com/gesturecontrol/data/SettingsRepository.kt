package com.gesturecontrol.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gesturecontrol.gestures.GestureToggles
import com.gesturecontrol.gestures.Sensitivity
import com.gesturecontrol.gestures.mapping.ScreenMapper
import com.gesturecontrol.gestures.pointer.PointerConfig
import com.gesturecontrol.gestures.pointer.PressMode
import com.gesturecontrol.vision.ClassifierKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** User-tunable cursor settings. */
data class PointerSettings(
    val enabled: Boolean = true,
    val pressMode: PressMode = PressMode.PINCH,
    /** Dead zone radius in screen widths. */
    val deadZone: Float = PointerConfig().deadZone,
    /** Null until the user has calibrated. */
    val calibration: ScreenMapper? = null,
) {
    val mapper: ScreenMapper get() = calibration ?: ScreenMapper.fromRegion()

    fun toConfig() = PointerConfig(pressMode = pressMode, deadZone = deadZone)
}

/** Simple user settings stored in DataStore. */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.settingsDataStore

    /** Drop analysis to ~10 fps while no hand is visible. */
    val powerSaveEnabled: Flow<Boolean> = store.data.map { it[POWER_SAVE] ?: true }

    suspend fun setPowerSaveEnabled(enabled: Boolean) {
        store.edit { it[POWER_SAVE] = enabled }
    }

    /** Pose recognition: the trained model by default, with automatic fallback to the rules. */
    val classifierKind: Flow<ClassifierKind> = store.data.map { prefs ->
        prefs[CLASSIFIER]?.let { runCatching { ClassifierKind.valueOf(it) }.getOrNull() } ?: ClassifierKind.MODEL
    }

    suspend fun setClassifierKind(kind: ClassifierKind) {
        store.edit { it[CLASSIFIER] = kind.name }
    }

    val pointerSettings: Flow<PointerSettings> = store.data.map { prefs ->
        val defaults = PointerSettings()
        PointerSettings(
            enabled = prefs[CURSOR_ENABLED] ?: defaults.enabled,
            pressMode = prefs[PRESS_MODE]?.let { runCatching { PressMode.valueOf(it) }.getOrNull() } ?: defaults.pressMode,
            deadZone = prefs[DEAD_ZONE] ?: defaults.deadZone,
            calibration = prefs[CALIBRATION]?.let { ScreenMapper.parse(it) },
        )
    }

    suspend fun setCursorEnabled(enabled: Boolean) {
        store.edit { it[CURSOR_ENABLED] = enabled }
    }

    suspend fun setPressMode(mode: PressMode) {
        store.edit { it[PRESS_MODE] = mode.name }
    }

    suspend fun setDeadZone(value: Float) {
        store.edit { it[DEAD_ZONE] = value }
    }

    /** Per-gesture sensitivity levels (0 = defaults); out-of-range stored values are clamped. */
    val sensitivity: Flow<Sensitivity> = store.data.map { prefs ->
        fun level(key: Preferences.Key<Int>) = (prefs[key] ?: 0).coerceIn(Sensitivity.MIN, Sensitivity.MAX)
        Sensitivity(
            swipe = level(SENS_SWIPE),
            finger = level(SENS_FINGER),
            palmHold = level(SENS_PALM),
            fistOpen = level(SENS_FIST),
            pinch = level(SENS_PINCH),
            scroll = level(SENS_SCROLL),
        )
    }

    suspend fun setSensitivity(value: Sensitivity) {
        store.edit {
            it[SENS_SWIPE] = value.swipe
            it[SENS_FINGER] = value.finger
            it[SENS_PALM] = value.palmHold
            it[SENS_FIST] = value.fistOpen
            it[SENS_PINCH] = value.pinch
            it[SENS_SCROLL] = value.scroll
        }
    }

    /** Which main gestures are switched on. */
    val gestureToggles: Flow<GestureToggles> = store.data.map { prefs ->
        GestureToggles(
            swipe = prefs[ON_SWIPE] ?: true,
            finger = prefs[ON_FINGER] ?: true,
            palmHold = prefs[ON_PALM] ?: true,
            fistOpen = prefs[ON_FIST] ?: true,
            pinch = prefs[ON_PINCH] ?: true,
            scroll = prefs[ON_SCROLL] ?: true,
        )
    }

    suspend fun setGestureToggles(value: GestureToggles) {
        store.edit {
            it[ON_SWIPE] = value.swipe
            it[ON_FINGER] = value.finger
            it[ON_PALM] = value.palmHold
            it[ON_FIST] = value.fistOpen
            it[ON_PINCH] = value.pinch
            it[ON_SCROLL] = value.scroll
        }
    }

    /** Null resets to the default (uncalibrated) mapping. */
    suspend fun setCalibration(mapper: ScreenMapper?) {
        store.edit { if (mapper == null) it.remove(CALIBRATION) else it[CALIBRATION] = mapper.serialize() }
    }

    private companion object {
        val POWER_SAVE = booleanPreferencesKey("power_save_enabled")
        val CURSOR_ENABLED = booleanPreferencesKey("cursor_enabled")
        val PRESS_MODE = stringPreferencesKey("press_mode")
        val DEAD_ZONE = floatPreferencesKey("dead_zone")
        val CALIBRATION = stringPreferencesKey("calibration")
        val CLASSIFIER = stringPreferencesKey("pose_classifier")
        val SENS_SWIPE = intPreferencesKey("sens_swipe")
        val SENS_FINGER = intPreferencesKey("sens_finger")
        val SENS_PALM = intPreferencesKey("sens_palm_hold")
        val SENS_FIST = intPreferencesKey("sens_fist_open")
        val SENS_PINCH = intPreferencesKey("sens_pinch")
        val SENS_SCROLL = intPreferencesKey("sens_scroll")
        val ON_SWIPE = booleanPreferencesKey("gesture_on_swipe")
        val ON_FINGER = booleanPreferencesKey("gesture_on_finger")
        val ON_PALM = booleanPreferencesKey("gesture_on_palm_hold")
        val ON_FIST = booleanPreferencesKey("gesture_on_fist_open")
        val ON_PINCH = booleanPreferencesKey("gesture_on_pinch")
        val ON_SCROLL = booleanPreferencesKey("gesture_on_scroll")
    }
}
