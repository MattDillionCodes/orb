package com.orbdash

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.text.DateFormat
import java.util.Date

/** One launchable app on the TV. */
data class TvApp(
    val label: String,
    val packageName: String,
    val component: ComponentName,
    /** Android's own category hint (ApplicationInfo.CATEGORY_*), or -1 if the app never declared one. */
    val declaredCategory: Int,
    /** 16:9 TV banner if the app ships one, otherwise its square icon. */
    val art: ImageBitmap?,
    val artIsBanner: Boolean,
    /** Small square icon for list rows. */
    val icon: ImageBitmap?,
    val isTvApp: Boolean,
)

class AppRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val prefs = context.getSharedPreferences("orbdash", Context.MODE_PRIVATE)

    /** All launchable apps, TV-native ones preferred, this launcher excluded. */
    fun loadApps(): List<TvApp> {
        val byPackage = LinkedHashMap<String, TvApp>()
        fun collect(category: String, isTv: Boolean) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val ai = ri.activityInfo
                if (ai.packageName == context.packageName || ai.packageName in byPackage) continue
                val banner = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
                val icon = ai.loadIcon(pm)
                byPackage[ai.packageName] = TvApp(
                    label = ri.loadLabel(pm).toString(),
                    packageName = ai.packageName,
                    component = ComponentName(ai.packageName, ai.name),
                    declaredCategory = ai.applicationInfo.category,
                    art = (banner ?: icon)?.toImageBitmap(if (banner != null) 320 else 192, if (banner != null) 180 else 192),
                    artIsBanner = banner != null,
                    icon = icon?.toImageBitmap(96, 96),
                    isTvApp = isTv,
                )
            }
        }
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, isTv = true)
        collect(Intent.CATEGORY_LAUNCHER, isTv = false)
        return byPackage.values.sortedBy { it.label.lowercase() }
    }

    /** Readout lines for the detail panel. Cheap enough to compute when a row gains focus. */
    fun details(app: TvApp): List<Pair<String, String>> = runCatching {
        val pi = pm.getPackageInfo(app.packageName, 0)
        val ai = pi.applicationInfo
        val bytes = listOfNotNull(ai?.sourceDir).plus(ai?.splitSourceDirs.orEmpty()).sumOf { File(it).length() }
        listOf(
            "VERSION" to (pi.versionName ?: "—"),
            "UPDATED" to DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(pi.lastUpdateTime)),
            "SIZE" to if (bytes > 0) "%.0f MB".format(bytes / 1_000_000.0) else "—",
            "TYPE" to if (app.isTvApp) "Made for TV" else "Phone app",
        )
    }.getOrDefault(emptyList())

    fun systemInfo(appCount: Int): List<Pair<String, String>> {
        val stat = StatFs(Environment.getDataDirectory().path)
        val dm = context.resources.displayMetrics
        return listOf(
            "DEVICE" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "ANDROID" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "STORAGE" to "%.1f GB free of %.1f GB".format(stat.availableBytes / 1e9, stat.totalBytes / 1e9),
            "SCREEN" to "${dm.widthPixels} × ${dm.heightPixels} · $appCount apps",
        )
    }

    fun launch(app: TvApp): Boolean {
        val intent = (if (app.isTvApp) pm.getLeanbackLaunchIntentForPackage(app.packageName) else null)
            ?: pm.getLaunchIntentForPackage(app.packageName)
            ?: Intent(Intent.ACTION_MAIN).setComponent(app.component)
        return startSafely(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).also { ok ->
            if (ok) prefs.edit().putString(KEY_LAST, app.packageName).apply()
        }
    }

    fun launchPackage(pkg: String): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        return startSafely(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Opens a system screen; on TVs that lack it, falls back to the main Settings app. */
    fun openSystem(action: String): Boolean =
        startSafely(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) ||
            startSafely(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun lastUsedPackage(): String? = prefs.getString(KEY_LAST, null)

    var soundsOn: Boolean
        get() = prefs.getBoolean(KEY_SOUNDS, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUNDS, value).apply()

    var humOn: Boolean
        get() = prefs.getBoolean(KEY_HUM, true)
        set(value) = prefs.edit().putBoolean(KEY_HUM, value).apply()

    var wallpaperId: String
        get() = prefs.getString(KEY_WALLPAPER, "living") ?: "living"
        set(value) = prefs.edit().putString(KEY_WALLPAPER, value).apply()

    private fun startSafely(intent: Intent): Boolean = try {
        context.startActivity(intent); true
    } catch (e: Exception) {
        false
    }

    private fun Drawable.toImageBitmap(w: Int, h: Int): ImageBitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, w, h)
        draw(Canvas(bmp))
        return bmp.asImageBitmap()
    }

    private companion object {
        const val KEY_LAST = "last_package"
        const val KEY_SOUNDS = "sounds_on"
        const val KEY_HUM = "hum_on"
        const val KEY_WALLPAPER = "wallpaper"
    }
}
