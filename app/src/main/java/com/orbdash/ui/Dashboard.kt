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
    /** Full-screen music light show. */
    data object Visualizer : Screen
}

private const val SETTINGS = "settings"
private const val RESUME = "resume"
private const val MUSIC = "music"
/** Home ids for app pods are "app:" + package name. */
private const val APP = "app:"

@Composable
fun Dashboard(apps: List<TvApp>, lastUsedPackage: String?, homeTicks: Int, repo: AppRepository, sfx: Sfx, playBoot: Boolean, player: com.orbdash.music.MusicPlayer) {
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
    var effectsMode by remember { mutableStateOf(repo.effectsMode) }
    var measuredSlow by remember { mutableIntStateOf(repo.measuredSlow) }
    val reduced = when (effectsMode) {
        "full" -> false
        "reduced" -> true
        else -> measuredSlow == 1
    }
    var wallpaper by remember { mutableStateOf(WALLPAPERS.firstOrNull { it.id == repo.wallpaperId } ?: WALLPAPERS[0]) }
    val wallpaperImage = rememberWallpaper(wallpaper)
    val energy = remember { Animatable(0f) }
    val focus = remember { FocusRequester() }

    val resumeApp = apps.firstOrNull { it.packageName == lastUsedPackage }
    val appsByPackage = remember(apps) { apps.associateBy { it.packageName } }

    // The home list opens as Settings, Music, then Resume in the focused middle pod,
    // with every app as its own pod, A to Z, below it. The wheel wraps, so Settings
    // sits two above Resume and the last apps loop round above it.
    val homeIds = buildList {
        add(SETTINGS)
        add(MUSIC)
        if (resumeApp != null) add(RESUME)
        apps.forEach { if (it.packageName != repo.systemSettingsPackage) add(APP + it.packageName) }
    }
    // Where focus lands when home opens: Resume, or Music when there's nothing to resume.
    val homeStart = if (resumeApp != null) homeIds.indexOf(RESUME) else homeIds.indexOf(MUSIC)
    fun plural(n: Int) = if (n == 1) "1 app" else "$n apps"
    // Built once per app list (reloaded each time you come back), not on every key press:
    // it reads saved "last opened" times and formats dates, which is slow on a TV's CPU.
    val musicPlaying = player.playing.value
    val homeItems = remember(apps, resumeApp, musicPlaying) { homeIds.map { id ->
        val app = appsByPackage[id.removePrefix(APP)]
        when {
            id == RESUME -> MenuItem("RESUME", resumeApp!!.label, "Pick up where you left off in ${resumeApp.label}.", Ico.RESUME)
            id == MUSIC -> MenuItem("MUSIC", if (musicPlaying) "playing" else null, "Your music with a light show, like the original's visualizer.", Ico.NOTE)
            app != null -> MenuItem(
                label = app.label,
                // categorize() labels each app (WATCH / MUSIC / GAMES); plain apps get no label.
                sub = categorize(app).takeIf { it != Category.OTHER }?.name,
                blurb = "Press OK to open ${app.label}. Last opened: ${repo.details(app).first().second.lowercase()}.",
                icon = Ico.GRID,
                thumb = app.icon,
            )
            else -> MenuItem("SETTINGS", null, "Wi-Fi, picture, sound and dashboard options.", Ico.GEAR)
        }
    } }
    homeIndex = homeIndex.coerceIn(0, homeItems.lastIndex)

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
            Entry(
                "Visual effects",
                when (effectsMode) {
                    "full" -> "Everything animated. Press OK for Reduced."
                    "reduced" -> "Background held still so slower TVs stay smooth. Press OK for Auto."
                    else -> "This TV was checked and set to ${if (measuredSlow == 1) "Reduced" else "Full"}. Press OK for Full."
                },
                icon = Ico.WAVE, kind = "DASHBOARD", group = dash, okLabel = "NEXT",
                value = if (effectsMode == "auto") "AUTO" else effectsMode.uppercase(),
                readouts = { listOf("NOW USING" to if (reduced) "Reduced" else "Full") },
            ) {
                effectsMode = when (effectsMode) { "auto" -> "full"; "full" -> "reduced"; else -> "auto" }
                repo.effectsMode = effectsMode
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

    // --- Music ---------------------------------------------------------------------------------
    val renderer = remember(player) { com.orbdash.music.VisualRenderer(player) }
    var musicScan by remember { mutableIntStateOf(0) } // bump to rescan after a permission grant
    var canRead by remember { mutableStateOf(com.orbdash.music.MusicLibrary.canRead(context)) }
    var wokeAt by remember { androidx.compose.runtime.mutableFloatStateOf(0f) } // last button press in the visualizer
    val readPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        canRead = granted; musicScan++
    }
    // A track waiting for the permission answer: the system dialog pauses the app, so playback
    // starts only once it's closed (otherwise the song would sit paused behind the dialog).
    var pendingTrack by remember { mutableIntStateOf(-1) }
    fun startTrack(i: Int) { player.play(i); wokeAt = clock.value; screen = Screen.Visualizer }
    val analysePermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        if (pendingTrack >= 0) { startTrack(pendingTrack); pendingTrack = -1 }
    }
    LaunchedEffect(screen == Screen.Section(MUSIC), musicScan) {
        if (screen != Screen.Section(MUSIC) && player.tracks.value.isNotEmpty()) return@LaunchedEffect
        player.tracks.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            listOf(com.orbdash.music.DemoTrack.get(context)) + com.orbdash.music.MusicLibrary.scan(context)
        }
    }
    fun playTrack(i: Int) {
        // Sound analysis needs this permission; ask once, before the first song starts.
        if (!com.orbdash.music.MusicLibrary.canAnalyse(context) && !repo.askedAnalyse) {
            repo.askedAnalyse = true
            pendingTrack = i
            analysePermission.launch(android.Manifest.permission.RECORD_AUDIO)
        } else startTrack(i)
    }
    fun musicEntries(): List<Entry> = buildList {
        val list = player.tracks.value
        val cur = list.getOrNull(player.current.intValue)
        if (cur != null) add(Entry(
            "Now playing", "${cur.title} · ${cur.artist}. Press OK to show the light show.", icon = Ico.WAVE, kind = "PLAYER",
            group = "NOW PLAYING", okLabel = "SHOW", value = if (player.playing.value) "PLAYING" else "PAUSED",
        ) { wokeAt = clock.value; screen = Screen.Visualizer })
        list.forEachIndexed { i, t ->
            val mins = t.durationMs / 60_000; val secs = (t.durationMs / 1000) % 60
            add(Entry(
                t.title, "Press OK to play it with the light show.", icon = Ico.NOTE, kind = "TRACK",
                group = if (i == 0) "BUILT-IN DEMO" else "ON THIS TV", okLabel = "PLAY",
                value = if (i == player.current.intValue && player.playing.value) "PLAYING" else null,
                readouts = { listOf("ARTIST" to t.artist, "ALBUM" to t.album.ifBlank { "—" }, "LENGTH" to "%d:%02d".format(mins, secs)) },
            ) { playTrack(i) })
        }
        if (!canRead) add(Entry(
            "Find music on this TV", "Let OrbDash see songs on USB drives and in the TV's Music folder.", icon = Ico.STORE, kind = "LIBRARY",
            group = "ON THIS TV", okLabel = "ALLOW",
        ) { readPermission.launch(com.orbdash.music.MusicLibrary.readPermission) })
        else if (list.size <= 1) add(Entry(
            "No music found", "Copy MP3 or FLAC files to a USB drive, plug it into the TV, and come back here.", icon = Ico.INFO, kind = "LIBRARY", group = "ON THIS TV",
        ))
    }

    val section = screen as? Screen.Section
    val entries: List<Entry> = when (section?.id) {
        SETTINGS -> settingsEntries()
        MUSIC -> musicEntries()
        else -> emptyList()
    }
    listIndex = listIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
    val subtitle = when (section?.id) {
        null -> null
        MUSIC -> "Soundtracks"
        else -> "TV and dashboard options"
    }

    fun flare() = scope.launch { energy.snapTo(1f); energy.animateTo(0f, tween(700)) }

    fun goHome() {
        when (screen) {
            Screen.Home -> {}
            Screen.Visualizer -> { sfx.back(); screen = Screen.Section(MUSIC) } // the music keeps playing
            else -> { sfx.back(); screen = Screen.Home }
        }
    }

    fun select() {
        if (screen == Screen.Visualizer) { wokeAt = clock.value; player.toggle(); return }
        // Info-only rows ignore OK entirely: no chime, no flare, nothing that implies an action.
        val action = if (screen is Screen.Section) entries.getOrNull(listIndex)?.action ?: return else null
        sfx.select(); flare()
        when (screen) {
            Screen.Home -> {
                val id = homeIds[homeIndex]
                when {
                    id == RESUME -> resumeApp?.let { repo.launch(it) }
                    id.startsWith(APP) -> appsByPackage[id.removePrefix(APP)]?.let { repo.launch(it) }
                    else -> { listIndex = lastIndexIn[id] ?: 0; screen = Screen.Section(id) }
                }
            }
            is Screen.Section -> action?.invoke()
            Screen.Visualizer -> {}
        }
    }

    fun move(delta: Int) {
        if (screen == Screen.Home) {
            // The pods form a wheel that wraps from the last item back to the first (matches MainMenu).
            val n = homeItems.size
            val next = if (n >= 2) ((homeIndex + delta) % n + n) % n else (homeIndex + delta).coerceIn(0, n - 1)
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
    // Opening home (launch, the Home button, or coming back from an app) focuses Resume.
    // Keyed on the resume app too: apps load in the background, so Resume appears a moment after launch.
    LaunchedEffect(homeTicks, resumeApp?.packageName) { homeIndex = homeStart }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // First run: time ~1.5 s of frames with full effects. Under ~40 fps means this TV's graphics chip
    // can't keep up, so switch to reduced effects and remember it.
    LaunchedEffect(booting) {
        if (booting || measuredSlow != -1 || effectsMode != "auto") return@LaunchedEffect
        kotlinx.coroutines.delay(500)
        val start = androidx.compose.runtime.withFrameNanos { it }
        var frames = 0
        var now = start
        while (now - start < 1_500_000_000L) { now = androidx.compose.runtime.withFrameNanos { it }; frames++ }
        val fps = frames / ((now - start) / 1e9f)
        measuredSlow = if (fps < 40f) 1 else 0
        repo.measuredSlow = measuredSlow
    }
    BackHandler(enabled = !booting) { goHome() }

    // The "camera": veins and tunnel converge on the orb at home and slide off to the corner in menus,
    // and every selection change tilts the view slightly so the layers parallax against each other.
    val home = screen == Screen.Home
    val focusTarget = if (home) Offset(0.29f, 0.5f) else Offset(0.97f, 0.12f)
    val veinFocusAnim by animateOffsetAsState(focusTarget, tween(450), label = "veins")
    val veinFocus = if (reduced) focusTarget else veinFocusAnim
    // Home keeps the camera level: the focused pod is always in the middle of the wheel.
    val tilt = if (home) 0f else (listIndex.coerceAtMost(8) - 4f) * 0.5f
    val cameraAnim by animateOffsetAsState(Offset(-0.004f * tilt, -0.012f * tilt), tween(600), label = "camera")
    val camera = if (reduced) Offset.Zero else cameraAnim
    val tunnelAnim by animateFloatAsState(if (home) 0.3f else 1f, tween(500), label = "tunnel")
    val tunnel = if (reduced) (if (home) 0.3f else 1f) else tunnelAnim

    androidx.compose.runtime.CompositionLocalProvider(LocalReducedEffects provides reduced) {
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (booting) { booting = false; return@onPreviewKeyEvent true } // any key skips the intro
                // In the light show: left/right change track, up/down change style, any key wakes the overlay.
                if (screen == Screen.Visualizer) {
                    wokeAt = clock.value
                    when (ev.key) {
                        Key.DirectionLeft -> { player.previous(); return@onPreviewKeyEvent true }
                        Key.DirectionRight -> { player.next(); return@onPreviewKeyEvent true }
                        Key.DirectionUp, Key.DirectionDown -> {
                            val n = com.orbdash.music.VisualRenderer.STYLES.size
                            renderer.style = (renderer.style + (if (ev.key == Key.DirectionUp) -1 else 1) + n) % n
                            sfx.move(); return@onPreviewKeyEvent true
                        }
                        else -> {}
                    }
                }
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
                Screen.Visualizer -> VisualizerScreen(player, renderer, clock, wokeAt)
            }
        }
        if (booting) Boot(clock, onStart = { sfx.boot() }, onDone = { booting = false })
    }
    }
}
