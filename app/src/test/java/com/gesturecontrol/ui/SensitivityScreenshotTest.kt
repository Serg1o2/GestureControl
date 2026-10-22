package com.gesturecontrol.ui

import androidx.compose.foundation.ScrollState
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.NightMode
import com.gesturecontrol.gestures.Sensitivity
import com.gesturecontrol.ui.sensitivity.SensitivityContent
import com.gesturecontrol.ui.theme.GestureControlTheme
import org.junit.Rule
import org.junit.Test

/** Screenshots of the gesture sensitivity screen (see MainScreenScreenshotTest for the paging). */
class SensitivityScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = device("ru"), maxPercentDifference = 0.1)

    private fun render(s: Sensitivity) = PAGES.forEach { px ->
        paparazzi.snapshot("scroll$px") {
            GestureControlTheme {
                SensitivityContent(s, onBack = {}, onChange = {}, onReset = {}, scrollState = ScrollState(px))
            }
        }
    }

    @Test
    fun russianChanged() = render(Sensitivity(swipe = 1, palmHold = -1, pinch = 2))

    @Test
    fun russianDarkChanged() {
        paparazzi.unsafeUpdateConfig(device("ru").copy(nightMode = NightMode.NIGHT))
        render(Sensitivity(swipe = 1, palmHold = -1, pinch = 2))
    }

    @Test
    fun englishDefault() {
        paparazzi.unsafeUpdateConfig(device("en"))
        render(Sensitivity())
    }

    private companion object {
        val PAGES = listOf(0, 900, 1800)

        fun device(locale: String) = DeviceConfig.PIXEL_5.copy(
            screenWidth = 720, screenHeight = 1000, xdpi = 320, ydpi = 320, density = Density.XHIGH, locale = locale,
        )
    }
}
