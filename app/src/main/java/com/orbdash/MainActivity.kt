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
    private val homeTicks = androidx.compose.runtime.mutableIntStateOf(0)
    private var resumed = false
    private var appLoads = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = AppRepository(this)
        sfx = Sfx(enabled = { repo.soundsOn }, humEnabled = { repo.humOn })
        player = com.orbdash.music.MusicPlayer(this).apply {
            onPlayingChanged = { syncHum() }
            others.onPlayingChanged = { syncHum() }
        }
        // Decided once: shouldPlayIntro() records this boot, so it must not run again on recomposition.
        val playBoot = savedInstanceState == null && repo.shouldPlayIntro()
        setContent {
            Dashboard(
                apps = apps.value,
                lastUsedPackage = lastUsed.value,
                homeTicks = homeTicks.intValue,
                repo = repo,
                sfx = sfx,
                playBoot = playBoot,
                player = player,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Apps may have been installed/removed while we were in the background. Drawing every
        // banner into a bitmap is slow, so do it off the main thread and swap the list in after.
        lastUsed.value = repo.lastUsedPackage()
        // Quick back-and-forth can start several loads; only the newest one's result is used.
        val load = ++appLoads
        kotlin.concurrent.thread(name = "load-apps") {
            val loaded = repo.loadApps()
            runOnUiThread { if (load == appLoads) apps.value = loaded }
        }
        resumed = true
        player.others.connect() // notification access may have just been switched on
        syncHum()
    }

    /** Music (ours or another app's) and the ambient hum never play together, and nothing hums in the background. */
    private fun syncHum() {
        if (resumed && !player.playing.value && !player.others.playing.value) sfx.startHum() else sfx.stopHum()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        sfx.stopHum() // never hum over someone's movie
    }

    // Music pauses on stop, not pause: pressing Home inside OrbDash briefly pauses the activity, and a
    // permission dialog does too, but neither actually leaves the screen.
    override fun onStop() {
        super.onStop()
        player.pause("OrbDash left the screen") // don't keep music going behind another app
    }

    override fun onDestroy() {
        player.release()
        player.others.disconnect()
        sfx.release()
        super.onDestroy()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        homeTicks.intValue++
    }
}
