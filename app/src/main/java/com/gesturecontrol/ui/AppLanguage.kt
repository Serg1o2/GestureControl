package com.gesturecontrol.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * In-app language choice (Russian by default, independent of the system language).
 *
 * Stored in plain SharedPreferences because it is needed synchronously in `attachBaseContext`,
 * before DataStore or Hilt are available. Every component that shows text wraps its base context
 * with [wrap]: the activity, the Application (toasts, notification channel), the services.
 */
object AppLanguage {
    enum class Lang(val tag: String) { RU("ru"), EN("en") }

    private const val PREFS = "app_language"
    private const val KEY = "lang"
    val DEFAULT = Lang.RU

    fun current(context: Context): Lang {
        val tag = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return Lang.entries.firstOrNull { it.tag == tag } ?: DEFAULT
    }

    /** A context whose resources use the chosen language. */
    fun wrap(base: Context): Context {
        val locale = Locale(current(base).tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** Saves the choice and recreates [activity] so that every screen is redrawn in the new language. */
    fun set(activity: Activity, lang: Lang) {
        if (lang == current(activity)) return
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, lang.tag).apply()
        updateApplication(activity.applicationContext)
        activity.recreate()
    }

    /** Applies the language to the Application's own resources (used by toasts and the service). */
    @Suppress("DEPRECATION")
    fun updateApplication(app: Context) {
        val locale = Locale(current(app).tag)
        Locale.setDefault(locale)
        val res = app.resources
        val config = Configuration(res.configuration).apply { setLocale(locale) }
        res.updateConfiguration(config, res.displayMetrics)
    }
}
