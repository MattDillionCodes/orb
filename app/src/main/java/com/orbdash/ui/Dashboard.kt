package com.orbdash.ui

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import com.orbdash.AppRepository
import com.orbdash.Category
import com.orbdash.Sfx
import com.orbdash.TvApp
import com.orbdash.categorize
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface Screen {
    data object Home : Screen
    data class Section(val id: String) : Screen
}

private const val ALL = "all"
private const val SETTINGS = "settings"
private const val RESUME = "resume"

@Composable
fun Dashboard(apps: List<TvApp>, lastUsedPackage: String?, homeTicks: Int, repo: AppRepository, sfx: Sfx, playBoot: Boolean) {
    val context = LocalContext.current
    val clock = rememberClock()
    val scope = rememberCoroutineScope()
    var booting by remember { mutableStateOf(playBoot) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var homeIndex by remember { mutableIntStateOf(0) }
    var listIndex by remember { mutableIntStateOf(0) }
    // Where you were in each list, so coming back to Watch lands on the app you left from.
    val lastIndexIn = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    var soundsOn by remember { mutableStateOf(repo.soundsOn) }
    var humOn by remember { mutableStateOf(repo.humOn) }
    var wallpaper by remember { mutableStateOf(WALLPAPERS.firstOrNull { it.id == repo.wallpaperId } ?: WALLPAPERS[0]) }
    val wallpaperImage = rememberWallpaper(wallpaper)
    val energy = remember { Animatable(0f) }
    val focus = remember { FocusRequester() }

    val buckets = remember(apps) { apps.groupBy { categorize(it) } }
    val resumeApp = apps.firstOrNull { it.packageName == lastUsedPackage }

    // Home pods. "Resume" takes the old disc slot at the top and only shows when there's something to resume.
    // Category pods only appear when they have apps in them: on a TV, an empty menu is a wasted trip.
    val homeIds = buildList {
        if (resumeApp != null) add(RESUME)
        for (c in listOf(Category.WATCH, Category.MUSIC, Category.GAMES)) if (!buckets[c].isNullOrEmpty()) add(c.name)
        add(ALL); add(SETTINGS)
    }
    fun count(c: Category) = buckets[c]?.size ?: 0
    fun plural(n: Int) = if (n == 1) "1 app" else "$n apps"
    val homeItems = homeIds.map { id ->
        when (id) {
            RESUME -> MenuItem("RESUME", resumeApp!!.label, "Pick up where you left off in ${resumeApp.label}.", Ico.RESUME)
            Category.WATCH.name -> MenuItem("WATCH", plural(count(Category.WATCH)), "Your video apps: shows, movies and YouTube.", Ico.PLAY)
            Category.MUSIC.name -> MenuItem("MUSIC", plural(count(Category.MUSIC)), "Your music, radio and podcast apps.", Ico.NOTE)
            Category.GAMES.name -> MenuItem("GAMES", plural(count(Category.GAMES)), "Your games and game-streaming apps.", Ico.PAD)
            ALL -> MenuItem("ALL APPS", plural(apps.size), "Every app on this TV, A to Z.", Ico.GRID)
            else -> MenuItem("SETTINGS", null, "Wi-Fi, picture, sound and dashboard options.", Ico.GEAR)
        }
    }
    homeIndex = homeIndex.coerceIn(0, homeItems.lastIndex)

    fun appEntries(list: List<TvApp>, emptyHint: String): List<Entry> =
        list.map { a ->
            Entry(a.label, "Press OK to open ${a.label}.", a.art, a.artIsBanner, thumb = a.icon, kind = "APP", readouts = { repo.details(a) }) { repo.launch(a) }
        }.ifEmpty {
            listOf(Entry("Get apps", "$emptyHint Press OK to open Google Play.", icon = Ico.STORE, kind = "GOOGLE PLAY") { repo.launchPackage("com.android.vending") })
        }

    fun onOff(b: Boolean) = if (b) "ON" else "OFF"
    fun settingsEntries(): List<Entry> {
        val next = WALLPAPERS[(WALLPAPERS.indexOf(wallpaper) + 1) % WALLPAPERS.size]
        val tv = "TV SETTINGS"
        val dash = "DASHBOARD"
        val opens = "Opens the TV's own settings."
        return listOf(
            // The things people actually come to Settings for, first.
            Entry("Wi-Fi & network", "Connect to Wi-Fi or Ethernet. $opens", icon = Ico.WIFI, kind = "TV", group = tv, readouts = { listOf("STATUS" to networkLabel(context)) }) {
                repo.openSystem(Settings.ACTION_WIFI_SETTINGS)
            },
            Entry("Picture", "Picture mode, brightness and resolution. $opens", icon = Ico.DISPLAY, kind = "TV", group = tv) { repo.openSystem(Settings.ACTION_DISPLAY_SETTINGS) },
            Entry("Sound", "Speakers, sound output and volume. $opens", icon = Ico.SPEAKER, kind = "TV", group = tv) { repo.openSystem(Settings.ACTION_SOUND_SETTINGS) },
            Entry(
                "Date & time", "Set the clock shown on this screen. $opens", icon = Ico.CLOCK, kind = "TV", group = tv,
                readouts = { listOf("NOW" to SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(Date())) },
            ) { repo.openSystem(Settings.ACTION_DATE_SETTINGS) },
            Entry("Apps", "Uninstall apps or free up space. $opens", icon = Ico.GRID, kind = "TV", group = tv, readouts = { listOf("INSTALLED" to plural(apps.size)) }) {
                repo.openSystem(Settings.ACTION_APPLICATION_SETTINGS)
            },
            Entry("All TV settings", "Everything else, in the TV's full settings menu.", icon = Ico.GEAR, kind = "TV", group = tv) { repo.openSystem(Settings.ACTION_SETTINGS) },
            // Look and feel of this dashboard.
            Entry(
                "Wallpaper", "Now showing ${wallpaper.name}. Press OK to switch to ${next.name}.",
                art = wallpaperImage, artIsBanner = true, icon = Ico.DISPLAY, value = wallpaper.name.uppercase(), kind = "DASHBOARD", group = dash, okLabel = "NEXT",
            ) {
                wallpaper = next
                repo.wallpaperId = wallpaper.id
            },
            Entry("Sound effects", "Chimes when you move and select. Press OK to turn ${if (soundsOn) "off" else "on"}.", icon = Ico.NOTE, value = onOff(soundsOn), kind = "DASHBOARD", group = dash, okLabel = if (soundsOn) "TURN OFF" else "TURN ON") {
                soundsOn = !soundsOn; repo.soundsOn = soundsOn
            },
            Entry("Background hum", "A soft drone while this screen is open. Stops when an app starts. Press OK to turn ${if (humOn) "off" else "on"}.", icon = Ico.WAVE, value = onOff(humOn), kind = "DASHBOARD", group = dash, okLabel = if (humOn) "TURN OFF" else "TURN ON") {
                humOn = !humOn; repo.humOn = humOn
                if (humOn) sfx.startHum() else sfx.stopHum()
            },
            // Read-only: no action, so the OK hint disappears instead of promising something.
            Entry("About this TV", "Model, Android version and free space.", icon = Ico.INFO, kind = "INFO", group = dash, readouts = { repo.systemInfo(apps.size) }),
        )
    }

    val section = screen as? Screen.Section
    val entries: List<Entry> = when (section?.id) {
        null -> emptyList()
        SETTINGS -> settingsEntries()
        ALL -> appEntries(apps, "No apps installed yet.")
        Category.WATCH.name -> appEntries(buckets[Category.WATCH].orEmpty(), "No video apps found.")
        Category.MUSIC.name -> appEntries(buckets[Category.MUSIC].orEmpty(), "No music apps found.")
        else -> appEntries(buckets[Category.GAMES].orEmpty(), "No games found.")
    }
    listIndex = listIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
    val subtitle = when (section?.id) {
        null -> null
        SETTINGS -> "TV and dashboard options"
        else -> if (entries.firstOrNull()?.kind == "APP") "${plural(entries.size)} · A to Z" else "Nothing here yet"
    }

    fun flare() = scope.launch { energy.snapTo(1f); energy.animateTo(0f, tween(700)) }

    fun goHome() {
        if (screen != Screen.Home) { sfx.back(); screen = Screen.Home }
    }

    fun select() {
        // Info-only rows ignore OK entirely: no chime, no flare, nothing that implies an action.
        val action = if (screen is Screen.Section) entries.getOrNull(listIndex)?.action ?: return else null
        sfx.select(); flare()
        when (screen) {
            Screen.Home -> when (val id = homeIds[homeIndex]) {
                RESUME -> resumeApp?.let { repo.launch(it) }
                else -> { listIndex = lastIndexIn[id] ?: 0; screen = Screen.Section(id) }
            }
            is Screen.Section -> action?.invoke()
        }
    }

    fun move(delta: Int) {
        if (screen == Screen.Home) {
            val next = (homeIndex + delta).coerceIn(0, homeItems.lastIndex)
            if (next != homeIndex) { homeIndex = next; sfx.move() }
        } else {
            val next = (listIndex + delta).coerceIn(0, (entries.size - 1).coerceAtLeast(0))
            if (next != listIndex) {
                listIndex = next; sfx.move()
                (screen as? Screen.Section)?.let { lastIndexIn[it.id] = next }
            }
        }
    }

    // Pressing Home while already on the dashboard returns to the orb.
    LaunchedEffect(homeTicks) { if (homeTicks > 0) { booting = false; screen = Screen.Home } }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler(enabled = !booting) { goHome() }

    // The "camera": veins and tunnel converge on the orb at home and slide off to the corner in menus,
    // and every selection change tilts the view slightly so the layers parallax against each other.
    val home = screen == Screen.Home
    val veinFocus by animateOffsetAsState(if (home) Offset(0.29f, 0.5f) else Offset(0.97f, 0.12f), tween(450), label = "veins")
    val tilt = if (home) (homeIndex - (homeItems.size - 1) / 2f) else (listIndex.coerceAtMost(8) - 4f) * 0.5f
    val camera by animateOffsetAsState(Offset(-0.004f * tilt, -0.012f * tilt), tween(600), label = "camera")
    val tunnel by animateFloatAsState(if (home) 0.3f else 1f, tween(500), label = "tunnel")

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (booting) { booting = false; return@onPreviewKeyEvent true } // any key skips the intro
                when (ev.key) {
                    Key.DirectionUp -> { move(-1); true }
                    Key.DirectionDown -> { move(1); true }
                    Key.DirectionRight -> { if (screen == Screen.Home) select(); true }
                    Key.DirectionLeft -> { goHome(); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA -> { select(); true }
                    Key.ButtonB -> { goHome(); true }
                    else -> false
                }
            },
    ) {
        Ambience(clock, veinFocus, camera, tunnel, wallpaperImage)
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                // Camera "flies into" a menu: new screen grows in, old one rushes past.
                if (targetState is Screen.Section) {
                    (fadeIn(tween(220)) + scaleIn(tween(240), initialScale = 0.86f)) togetherWith (fadeOut(tween(160)) + scaleOut(tween(220), targetScale = 1.25f))
                } else {
                    (fadeIn(tween(220)) + scaleIn(tween(240), initialScale = 1.2f)) togetherWith (fadeOut(tween(160)) + scaleOut(tween(220), targetScale = 0.85f))
                }
            },
            label = "screens",
        ) { s ->
            when (s) {
                Screen.Home -> MainMenu(homeItems, homeIndex, clock, energy.value, camera)
                is Screen.Section -> ListScreen(
                    title = homeItems.getOrNull(homeIds.indexOf(s.id))?.label ?: s.id,
                    subtitle = subtitle,
                    entries = entries,
                    index = listIndex,
                    clock = clock,
                )
            }
        }
        if (booting) Boot(clock, onStart = { sfx.boot() }, onDone = { booting = false })
    }
}
