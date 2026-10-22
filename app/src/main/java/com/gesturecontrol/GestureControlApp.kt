package com.gesturecontrol

import android.app.Application
import android.content.Context
import com.gesturecontrol.ui.AppLanguage
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GestureControlApp : Application() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLanguage.wrap(base))

    override fun onCreate() {
        super.onCreate()
        AppLanguage.updateApplication(this)
    }
}
