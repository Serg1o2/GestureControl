package com.gesturecontrol.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledApp(val packageName: String, val label: String, val icon: Bitmap?)

/**
 * Lists apps that have a launcher entry. Visibility on Android 11+ comes from the
 * MAIN/LAUNCHER `<queries>` element in the manifest (no QUERY_ALL_PACKAGES needed).
 */
@Singleton
class InstalledAppsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun loadLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    icon = runCatching { pm.getApplicationIcon(info).toBitmap(ICON_PX, ICON_PX) }.getOrNull(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun labelOf(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private companion object {
        const val ICON_PX = 96
    }
}
