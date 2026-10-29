package com.orbdash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.orbdash.ui.Dashboard

class MainActivity : ComponentActivity() {
    private lateinit var repo: AppRepository
    private lateinit var sfx: Sfx
    private lateinit var player: com.orbdash.music.MusicPlayer
    private val apps = mutableStateOf<List<TvApp>>(emptyList())
    private val lastUsed = mutableStateOf<String?>(null)
    /** Bumped when Home is pressed while we're already in front, so the UI can return to the orb. */
    private val homeTicks = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = AppRepository(this)
        sfx = Sfx(enabled = { repo.soundsOn }, humEnabled = { repo.humOn })
        // Music and the ambient hum never play together.
        player = com.orbdash.music.MusicPlayer(this).apply { onPlayingChanged = { playing -> if (playing) sfx.stopHum() else sfx.startHum() } }
        setContent {
            Dashboard(
                apps = apps.value,
                lastUsedPackage = lastUsed.value,
                homeTicks = homeTicks.value,
                repo = repo,
                sfx = sfx,
                playBoot = savedInstanceState == null && repo.shouldPlayIntro(),
                player = player,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Apps may have been installed/removed while we were in the background. Drawing every
        // banner into a bitmap is slow, so do it off the main thread and swap the list in after.
        lastUsed.value = repo.lastUsedPackage()
        kotlin.concurrent.thread(name = "load-apps") {
            val loaded = repo.loadApps()
            runOnUiThread { apps.value = loaded }
        }
        if (!player.playing.value) sfx.startHum()
    }

    override fun onPause() {
        super.onPause()
        sfx.stopHum() // never hum over someone's movie
        player.pause() // and don't keep music going behind another app
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        homeTicks.value++
    }
}
