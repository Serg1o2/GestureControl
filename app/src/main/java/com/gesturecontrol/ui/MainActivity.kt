package com.gesturecontrol.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import com.gesturecontrol.ui.cursor.CalibrationScreen
import com.gesturecontrol.ui.custom.CustomGesturesScreen
import com.gesturecontrol.ui.custom.GestureEditorScreen
import com.gesturecontrol.ui.custom.GestureEditorViewModel
import com.gesturecontrol.ui.cursor.CursorSettingsScreen
import com.gesturecontrol.ui.zones.ZoneAppsScreen
import com.gesturecontrol.ui.zones.ZoneEditorScreen
import com.gesturecontrol.ui.zones.ZoneEditorViewModel
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gesturecontrol.ui.apps.AppSelectionScreen
import com.gesturecontrol.ui.debug.DebugScreen
import com.gesturecontrol.ui.main.AccessibilityDisclosureScreen
import com.gesturecontrol.ui.main.MainScreen
import com.gesturecontrol.ui.sensitivity.SensitivityScreen
import com.gesturecontrol.ui.theme.GestureControlTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GestureControlTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = Routes.MAIN) {
                    composable(Routes.MAIN) {
                        MainScreen(
                            onOpenApps = { nav.navigate(Routes.APPS) },
                            onOpenDebug = { nav.navigate(Routes.DEBUG) },
                            onOpenAccessibilityDisclosure = { nav.navigate(Routes.ACCESSIBILITY_DISCLOSURE) },
                            onOpenZones = { nav.navigate(Routes.ZONES) },
                            onOpenCursorSettings = { nav.navigate(Routes.CURSOR_SETTINGS) },
                            onOpenCustom = { nav.navigate(Routes.CUSTOM) },
                            onOpenSensitivity = { nav.navigate(Routes.SENSITIVITY) },
                        )
                    }
                    composable(Routes.CURSOR_SETTINGS) {
                        CursorSettingsScreen(
                            onBack = { nav.popBackStack() },
                            onCalibrate = { nav.navigate(Routes.CALIBRATION) },
                        )
                    }
                    composable(Routes.SENSITIVITY) { SensitivityScreen(onBack = { nav.popBackStack() }) }
                    composable(Routes.CALIBRATION) { CalibrationScreen(onDone = { nav.popBackStack() }) }
                    composable(Routes.CUSTOM) {
                        CustomGesturesScreen(
                            onBack = { nav.popBackStack() },
                            onEdit = { profile, id -> nav.navigate("${Routes.CUSTOM_EDITOR}/${Uri.encode(profile)}/${id ?: 0}") },
                        )
                    }
                    composable(
                        "${Routes.CUSTOM_EDITOR}/{${GestureEditorViewModel.ARG_PROFILE}}/{${GestureEditorViewModel.ARG_ID}}",
                        arguments = listOf(
                            navArgument(GestureEditorViewModel.ARG_PROFILE) { type = NavType.StringType },
                            navArgument(GestureEditorViewModel.ARG_ID) { type = NavType.LongType },
                        ),
                    ) {
                        GestureEditorScreen(onDone = { nav.popBackStack() })
                    }
                    composable(Routes.ZONES) {
                        ZoneAppsScreen(
                            onBack = { nav.popBackStack() },
                            onOpenEditor = { pkg -> nav.navigate("${Routes.ZONE_EDITOR}/${Uri.encode(pkg)}") },
                        )
                    }
                    composable(
                        "${Routes.ZONE_EDITOR}/{${ZoneEditorViewModel.ARG_PACKAGE}}",
                        arguments = listOf(navArgument(ZoneEditorViewModel.ARG_PACKAGE) { type = NavType.StringType }),
                    ) {
                        ZoneEditorScreen(onBack = { nav.popBackStack() })
                    }
                    composable(Routes.ACCESSIBILITY_DISCLOSURE) {
                        AccessibilityDisclosureScreen(onDone = { nav.popBackStack() })
                    }
                    composable(Routes.APPS) { AppSelectionScreen(onBack = { nav.popBackStack() }) }
                    composable(Routes.DEBUG) { DebugScreen(onBack = { nav.popBackStack() }) }
                }
            }
        }
    }
}

object Routes {
    const val MAIN = "main"
    const val APPS = "apps"
    const val DEBUG = "debug"
    const val ACCESSIBILITY_DISCLOSURE = "accessibility_disclosure"
    const val ZONES = "zones"
    const val ZONE_EDITOR = "zone_editor"
    const val CURSOR_SETTINGS = "cursor_settings"
    const val CALIBRATION = "calibration"
    const val CUSTOM = "custom"
    const val CUSTOM_EDITOR = "custom_editor"
    const val SENSITIVITY = "sensitivity"
}
