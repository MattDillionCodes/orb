package com.orbdash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.orbdash.ui.Dashboard

class MainActivity : ComponentActivity() {
    private lateinit var repo: AppRepository
    private lateinit var sfx: Sfx
    private val apps = mutableStateOf<List<TvApp>>(emptyList())
    private val lastUsed = mutableStateOf<String?>(null)
    /** Bumped when Home is pressed while we're already in front, so the UI can return to the orb. */
    private val homeTicks = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = AppRepository(this)
        sfx = Sfx(enabled = { repo.soundsOn }, humEnabled = { repo.humOn })
        setContent {
            Dashboard(
                apps = apps.value,
                lastUsedPackage = lastUsed.value,
                homeTicks = homeTicks.value,
                repo = repo,
                sfx = sfx,
                playBoot = savedInstanceState == null && repo.shouldPlayIntro(),
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
        sfx.startHum()
    }

    override fun onPause() {
        super.onPause()
        sfx.stopHum() // never hum over someone's movie
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        homeTicks.value++
    }
}
