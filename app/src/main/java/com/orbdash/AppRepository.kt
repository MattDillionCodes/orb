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

    /**
     * What a viewer actually wants to know about an app on the couch: when they last used it.
     * (Version numbers and install sizes are developer details, so they're left out.)
     */
    fun details(app: TvApp): List<Pair<String, String>> = listOf(
        "LAST OPENED" to relativeDay(prefs.getLong(KEY_OPENED + app.packageName, 0L)),
        "MADE FOR" to if (app.isTvApp) "TV remote" else "Phone (may need a mouse)",
    )

    private fun relativeDay(millis: Long): String {
        if (millis == 0L) return "Not yet"
        val days = ((System.currentTimeMillis() - millis) / 86_400_000L).toInt()
        return when (days) {
            0 -> "Today"
            1 -> "Yesterday"
            in 2..13 -> "$days days ago"
            else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
        }
    }

    /** The intro plays once each time the TV powers on, not every time Android restarts the launcher. */
    fun shouldPlayIntro(): Boolean {
        val boot = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
        if (boot != -1 && prefs.getInt(KEY_INTRO_BOOT, -2) == boot) return false
        prefs.edit().putInt(KEY_INTRO_BOOT, boot).apply()
        return true
    }

    fun systemInfo(appCount: Int): List<Pair<String, String>> {
        val stat = StatFs(Environment.getDataDirectory().path)
        return listOf(
            "DEVICE" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "ANDROID" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "FREE SPACE" to "%.1f GB of %.1f GB · $appCount apps".format(stat.availableBytes / 1e9, stat.totalBytes / 1e9),
        )
    }

    /** The TV's own Settings app. Home already has a Settings pod (with "All TV settings"), so it's hidden from the app list. */
    val systemSettingsPackage: String? by lazy {
        pm.resolveActivity(Intent(android.provider.Settings.ACTION_SETTINGS), 0)?.activityInfo?.packageName
    }

    fun launch(app: TvApp): Boolean {
        val intent = (if (app.isTvApp) pm.getLeanbackLaunchIntentForPackage(app.packageName) else null)
            ?: pm.getLaunchIntentForPackage(app.packageName)
            ?: Intent(Intent.ACTION_MAIN).setComponent(app.component)
        return startSafely(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).also { ok ->
            if (ok) prefs.edit().putString(KEY_LAST, app.packageName).putLong(KEY_OPENED + app.packageName, System.currentTimeMillis()).apply()
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
        get() = prefs.getBoolean(KEY_HUM, false) // off by default: a humming TV is confusing
        set(value) = prefs.edit().putBoolean(KEY_HUM, value).apply()

    /** "auto" (decided by [measuredSlow]), "full" or "reduced". */
    var effectsMode: String
        get() = prefs.getString(KEY_EFFECTS, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_EFFECTS, value).apply()

    /** Result of the first-run frame-time check: -1 not measured yet, 1 = this TV is too slow for full effects. */
    var measuredSlow: Int
        get() = prefs.getInt(KEY_SLOW, -1)
        set(value) = prefs.edit().putInt(KEY_SLOW, value).apply()

    /** Whether we've already asked for the audio-analysis permission (only ask once). */
    var askedAnalyse: Boolean
        get() = prefs.getBoolean(KEY_ASKED_ANALYSE, false)
        set(value) = prefs.edit().putBoolean(KEY_ASKED_ANALYSE, value).apply()

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
        const val KEY_OPENED = "opened_"
        const val KEY_EFFECTS = "effects_mode"
        const val KEY_ASKED_ANALYSE = "asked_analyse"
        const val KEY_SLOW = "measured_slow"
        const val KEY_INTRO_BOOT = "intro_boot"
    }
}
