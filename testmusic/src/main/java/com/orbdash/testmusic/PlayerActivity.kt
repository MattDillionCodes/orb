package com.orbdash.testmusic

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Starts the player and closes, leaving the music running in the background. */
class PlayerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startForegroundService(Intent(this, PlayerService::class.java))
        finish()
    }
}

/**
 * Plays a simple synthesized beat and publishes a media session, like a real music app does. It runs
 * as a foreground service, as real players do, so Android doesn't freeze it in the background.
 */
class PlayerService : Service() {
    private val songs = listOf("Neon Drift" to 110f, "Static Bloom" to 147f, "Low Orbit" to 98f)
    private var song = 0
    @Volatile private var playing = false
    private var startedAt = 0L
    private var pausedAt = 0L
    @Volatile private var alive = true
    private lateinit var session: MediaSession

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("play", "Playback", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "play").setContentTitle("Test Music").setSmallIcon(android.R.drawable.ic_media_play).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(1, n)
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        session = MediaSession(this, "TestMusic").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = play()
                override fun onPause() = pause()
                override fun onSkipToNext() = change(1)
                override fun onSkipToPrevious() = change(-1)
            })
            isActive = true
        }
        Thread(::audioLoop, "test-music").apply { isDaemon = true }.start()
        play()
    }

    override fun onDestroy() { alive = false; playing = false; session.release(); super.onDestroy() }

    private fun play() { startedAt = SystemClock.elapsedRealtime() - pausedAt; playing = true; publish() }
    private fun pause() { pausedAt = SystemClock.elapsedRealtime() - startedAt; playing = false; publish() }
    private fun change(d: Int) { song = (song + d + songs.size) % songs.size; pausedAt = 0; play() }

    private fun publish() {
        val (title, _) = songs[song]
        session.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "The Test Band")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Emulator Sessions")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, 180_000)
            .build())
        val pos = if (playing) SystemClock.elapsedRealtime() - startedAt else pausedAt
        session.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
            .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, pos, 1f, SystemClock.elapsedRealtime())
            .build())
    }

    /** Kick drum and a bass note on every beat, at the current song's tempo. */
    private fun audioLoop() {
        val rate = 22050
        val buf = ShortArray(rate / 20)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(buf.size * 4)
            .build()
        track.play()
        var n = 0L
        while (alive) {
            val bpm = songs[song].second
            val beatLen = (rate * 60 / bpm).toLong()
            for (i in buf.indices) {
                val t = (n % beatLen).toFloat() / rate
                val kick = sin(2 * PI * (50 + 90 * exp(-t * 30)) * t).toFloat() * exp(-t * 9)
                val bass = sin(2 * PI * (55.0 * (1 + (n / beatLen) % 4 / 4.0)) * n / rate).toFloat() * 0.3f
                buf[i] = if (playing) ((kick * 0.7f + bass) * 12000).toInt().toShort() else 0
                n++
            }
            track.write(buf, 0, buf.size)
        }
        track.release()
    }
}
