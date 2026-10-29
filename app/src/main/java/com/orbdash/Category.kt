package com.orbdash

import android.content.pm.ApplicationInfo

/** The small label shown next to an app's name on its home pod (OTHER shows no label). */
enum class Category { WATCH, MUSIC, GAMES, OTHER }

/**
 * Package-name hints for popular apps, since many TV apps never declare an
 * ApplicationInfo.category. Matched with startsWith, so "com.netflix" covers "com.netflix.ninja".
 */
val WATCH_HINTS = listOf(
    "com.google.android.youtube.tv", "com.netflix", "com.amazon.amazonvideo", "com.disney",
    "com.hulu", "com.hbo", "com.wbd", "com.plexapp", "tv.pluto", "com.apple.atve",
    "com.peacocktv", "com.cbs", "com.paramount", "com.google.android.videos", "com.google.android.tv",
    "com.crunchyroll", "com.tubitv", "com.sling", "org.xbmc.kodi", "com.google.android.apps.youtube.unplugged",
)
val MUSIC_HINTS = listOf(
    "com.spotify", "com.google.android.youtube.tvmusic", "com.google.android.apps.youtube.music",
    "com.pandora", "com.amazon.music", "com.apple.android.music", "com.soundcloud", "deezer",
    "com.aspiro.tidal", "com.audible", "tunein",
)
val GAMES_HINTS = listOf(
    "com.nvidia", "com.valvesoftware", "com.microsoft.xcloud", "com.gamepass", "com.retroarch",
    "com.google.stadia", "com.netflix.NGP", "com.antutu",
)

fun categorize(app: TvApp): Category {
    // TODO(human): decide which bucket an app belongs in. Signals available:
    //   app.declaredCategory  -> ApplicationInfo.CATEGORY_VIDEO / _AUDIO / _GAME, or -1 if undeclared
    //   app.packageName       -> match against WATCH_HINTS / MUSIC_HINTS / GAMES_HINTS above
    // Placeholder: trusts only the declared category, so many real apps land in OTHER.
    return when (app.declaredCategory) {
        ApplicationInfo.CATEGORY_VIDEO -> Category.WATCH
        ApplicationInfo.CATEGORY_AUDIO -> Category.MUSIC
        ApplicationInfo.CATEGORY_GAME -> Category.GAMES
        else -> Category.OTHER
    }
}
