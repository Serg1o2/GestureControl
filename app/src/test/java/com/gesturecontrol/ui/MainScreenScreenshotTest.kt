package com.gesturecontrol.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.material3.SnackbarHostState
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.NightMode
import com.gesturecontrol.service.ServiceStatus
import com.gesturecontrol.ui.main.MainContent
import com.gesturecontrol.ui.main.MainUiState
import com.gesturecontrol.ui.theme.GestureControlTheme
import com.gesturecontrol.vision.ClassifierKind
import com.gesturecontrol.vision.ClassifierStatus
import org.junit.Rule
import org.junit.Test

/**
 * Screenshots of the main screen rendered on the JVM (layoutlib), to review the design without a device.
 * Record: ./gradlew :app:recordPaparazziDebug; images in app/src/test/snapshots/images.
 * Paparazzi scales images down to 1000 px, so the page is shot in screens of a 360 x 500 dp viewport
 * (xhdpi, 720 x 1000 px) at increasing scroll offsets.
 */
class MainScreenScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = device("ru"), maxPercentDifference = 0.1)

    private fun render(state: MainUiState, status: String, setupDone: Boolean, scrollPx: Int) {
        paparazzi.snapshot("scroll$scrollPx") {
            GestureControlTheme {
                MainContent(
                    state = state,
                    statusText = status,
                    cameraGranted = true,
                    notificationsGranted = setupDone,
                    accessibilityEnabled = setupDone,
                    snackbar = SnackbarHostState(),
                    onToggle = {}, onRequestCamera = {}, onRequestNotifications = {},
                    onOpenAccessibilityDisclosure = {}, onOpenApps = {}, onOpenZones = {},
                    onOpenCursorSettings = {}, onOpenCustom = {}, onOpenDebug = {},
                    onPowerSaveChange = {}, onUseModelChange = {},
                    scrollState = ScrollState(scrollPx),
                )
            }
        }
    }

    private val off = MainUiState(status = ServiceStatus.Off, selectedCount = 0)
    private val on = MainUiState(
        status = ServiceStatus.Active("com.spotify.music", "Spotify"),
        selectedCount = 3,
        classifier = ClassifierKind.MODEL,
        classifierStatus = ClassifierStatus(ClassifierKind.MODEL, ClassifierKind.MODEL, "20261022-1415"),
    )

    @Test
    fun russianLightSetupIncomplete() = PAGES.forEach { render(off, "", setupDone = false, scrollPx = it) }

    @Test
    fun russianDarkActive() {
        paparazzi.unsafeUpdateConfig(device("ru").copy(nightMode = NightMode.NIGHT))
        PAGES.forEach { render(on, "Активно: Spotify", setupDone = true, scrollPx = it) }
    }

    @Test
    fun englishLightActive() {
        paparazzi.unsafeUpdateConfig(device("en"))
        PAGES.forEach { render(on, "Active: Spotify", setupDone = true, scrollPx = it) }
    }

    private companion object {
        val PAGES = listOf(0, 900, 1800, 2700, 3600, 4500)

        fun device(locale: String) = DeviceConfig.PIXEL_5.copy(
            screenWidth = 720,
            screenHeight = 1000,
            xdpi = 320,
            ydpi = 320,
            density = Density.XHIGH,
            locale = locale,
        )
    }
}
