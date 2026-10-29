package com.orbdash.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.orbdash.music.MusicPlayer
import com.orbdash.music.VisualRenderer
import kotlinx.coroutines.delay

/**
 * Full-screen "now playing": the light show stretched across the TV, with the track info and remote
 * hints on top. The overlay fades away after a few seconds so it's just the show; any button
 * ([wokeAt] = clock time of the last press) brings it back.
 */
@Composable
fun VisualizerScreen(player: MusicPlayer, renderer: VisualRenderer, clock: State<Float>, wokeAt: Float) {
    // Render only while this screen is visible and the app is in front.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(renderer, lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> renderer.start()
                Lifecycle.Event.ON_PAUSE -> renderer.stop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) renderer.start()
        onDispose { lifecycle.removeObserver(observer); renderer.stop() }
    }

    // Either one of our tracks, or whatever the followed music app is playing.
    val others = player.others
    val following = player.followingOther.value
    val own = player.tracks.value.getOrNull(player.current.intValue)
    val title = if (following) others.title.value ?: others.app.value else own?.title
    val details = if (following) listOfNotNull(others.artist.value, others.album.value?.takeIf { it.isNotBlank() }, others.app.value?.let { "in $it" })
        else listOfNotNull(own?.artist, own?.album?.takeIf { it.isNotBlank() })
    val hasMusic = title != null
    val playing = player.audible()
    var posMs by remember { mutableIntStateOf(0) }
    var durMs by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { posMs = player.positionMs(); durMs = player.durationMs(); delay(500) } }

    // Overlay shows for 4 s after the last button press (and whenever paused).
    // derivedStateOf: the clock ticks every frame, but this screen only needs to rebuild when the answer flips.
    val showing by androidx.compose.runtime.remember(playing, wokeAt) { androidx.compose.runtime.derivedStateOf { !playing || clock.value - wokeAt < 4f } }
    val overlay by animateFloatAsState(if (showing) 1f else 0f, tween(if (showing) 200 else 900), label = "overlay")

    Box(Modifier.fillMaxSize()) {
        // The light show: a small image stretched to full screen with smooth (bilinear) filtering.
        Canvas(Modifier.fillMaxSize()) {
            renderer.frame.intValue // redraw whenever a new frame is published
            drawImage(renderer.image(), dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low)
        }

        Column(
            Modifier
                .alpha(overlay)
                .padding(start = 48.dp, top = 32.dp),
        ) {
            BasicText("NOW PLAYING  ·  ${VisualRenderer.STYLES[renderer.style]}", style = dashText(15.sp, Dash.Glow, bold = true, spacing = 4.sp))
            BasicText(title ?: "Nothing playing", style = dashText(40.sp, Dash.Core, bold = true), maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            BasicText(details.joinToString("  ·  "), style = dashText(20.sp, Dash.Text), maxLines = 1)
        }

        val failed = !following && player.error.value != null
        if (!playing && hasMusic) {
            BasicText(if (failed) "CAN'T PLAY" else "PAUSED", style = dashText(30.sp, Dash.Core, bold = true, spacing = 10.sp), modifier = Modifier.align(Alignment.Center).alpha(0.85f))
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .alpha(overlay)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                .padding(start = 48.dp, end = 48.dp, bottom = 26.dp, top = 20.dp),
        ) {
            // Progress bar with elapsed / total time.
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(mmss(posMs), style = dashText(16.sp, Dash.Text, bold = true), modifier = Modifier.width(56.dp))
                Box(Modifier.weight(1f).height(6.dp).background(Dash.Moss, RoundedCornerShape(3.dp))) {
                    val f = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
                    Box(Modifier.fillMaxWidth(f).height(6.dp).background(Brush.horizontalGradient(listOf(Dash.Green, Dash.Glow)), RoundedCornerShape(3.dp)))
                }
                BasicText(mmss(durMs), style = dashText(16.sp, Dash.Text, bold = true), modifier = Modifier.padding(start = 12.dp))
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph("◀▶", Color(0xFF3C5A33), "TRACK")
                Glyph("OK", Dash.ButtonA, if (failed) "TRY AGAIN" else if (playing) "PAUSE" else "PLAY")
                Glyph("▲▼", Color(0xFF3C5A33), "STYLE")
                Glyph("◀", Dash.ButtonB, "BACK")
            }
        }
    }
}

private fun mmss(ms: Int): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }
