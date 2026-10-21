package com.gesturecontrol.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gesturecontrol.ui.apps.AppSelectionScreen
import com.gesturecontrol.ui.debug.DebugScreen
import com.gesturecontrol.ui.main.MainScreen
import com.gesturecontrol.ui.theme.GestureControlTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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
                        )
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
}
