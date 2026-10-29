package com.orbdash.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat

/**
 * Exists only so the user can grant ORB UI "notification access". Android uses that one switch to
 * decide who may see other apps' media sessions; ORB UI never reads any notifications.
 */
class MediaListener : NotificationListenerService()

/**
 * What another music app (Spotify, YouTube Music, ...) is playing, and remote control of it.
 *
 * Music apps publish a media session so headphones, car stereos and the lock screen can show the song
 * and send play/pause back. With notification access, ORB UI can follow those sessions too.
 */
class OtherApps(private val context: Context) {
    val access = mutableStateOf(false)
    /** The app being followed, by name ("Spotify"), or null when no other app has a session. */
    val app = mutableStateOf<String?>(null)
    val title = mutableStateOf<String?>(null)
    val artist = mutableStateOf<String?>(null)
    val album = mutableStateOf<String?>(null)
    val playing = mutableStateOf(false)

    var onPlayingChanged: (Boolean) -> Unit = {}

    private val component = ComponentName(context, MediaListener::class.java)
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var connected = false

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { follow(it.orEmpty()) }
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = readMetadata()
        override fun onPlaybackStateChanged(state: PlaybackState?) = readState()
        override fun onSessionDestroyed() = follow(activeSessions())
    }

    fun hasAccess() = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Start following sessions; call on every resume, since access may have just been granted. */
    fun connect() {
        access.value = hasAccess()
        if (!access.value || connected) return
        connected = runCatching { sessions.addOnActiveSessionsChangedListener(sessionsChanged, component, main) }.isSuccess
        follow(activeSessions())
    }

    fun disconnect() {
        runCatching { sessions.removeOnActiveSessionsChangedListener(sessionsChanged) }
        connected = false
        use(null)
    }

    /** Opens the system screen where the user switches notification access on for ORB UI. */
    fun openAccessSettings(): Boolean {
        val detail = if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString()) else null
        val general = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        // Google TV often hides this screen, so try both and report whether either opened.
        return listOfNotNull(detail, general).any { i ->
            runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
    }

    fun toggle() { val c = controller ?: return; if (playing.value) c.transportControls.pause() else c.transportControls.play() }
    fun pause() { if (playing.value) controller?.transportControls?.pause() }
    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }

    fun durationMs(): Int = controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.toInt()?.coerceAtLeast(0) ?: 0

    /** Sessions only report position at their last update, so move it on by the time since then. */
    fun positionMs(): Int {
        val s = controller?.playbackState ?: return 0
        val since = if (s.state == PlaybackState.STATE_PLAYING) (SystemClock.elapsedRealtime() - s.lastPositionUpdateTime) * s.playbackSpeed else 0f
        val pos = (s.position + since).toLong()
        val dur = durationMs()
        return (if (dur > 0) pos.coerceIn(0, dur.toLong()) else pos.coerceAtLeast(0)).toInt()
    }

    private fun activeSessions(): List<MediaController> =
        runCatching { sessions.getActiveSessions(component) }.getOrDefault(emptyList())

    /** Follow the app that's playing; failing that, the most recent one (Android lists newest first). */
    private fun follow(list: List<MediaController>) {
        // Skip idle or broken sessions (a disconnected Bluetooth speaker, an app that never played).
        val usable = setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED, PlaybackState.STATE_BUFFERING)
        val others = list.filter { it.packageName != context.packageName && it.playbackState?.state in usable }
        use(others.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: others.firstOrNull())
    }

    private fun use(c: MediaController?) {
        if (c?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = c
            c?.registerCallback(callback, main)
        }
        app.value = c?.let { label(it.packageName) }
        readMetadata(); readState()
    }

    private fun readMetadata() {
        val m = controller?.metadata
        title.value = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        artist.value = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        album.value = m?.getString(MediaMetadata.METADATA_KEY_ALBUM)
    }

    private fun readState() {
        val on = controller?.playbackState?.state.let { it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING }
        if (playing.value != on) { playing.value = on; onPlayingChanged(on) }
    }

    private fun label(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
