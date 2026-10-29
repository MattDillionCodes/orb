package com.orbdash.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * The idle screen, like the original dashboard's: the room goes dark, the wallpaper slowly pans, and the
 * orb wanders with the time beneath it. Nothing stays in one place for long, so a TV left on the home
 * screen for hours doesn't keep one bright picture burned into the same pixels.
 *
 * [nowPlaying] adds a line under the clock while music plays. [onCovered] fires once it has fully faded in,
 * so the caller can stop drawing what's behind it. Any button wakes it (handled by the caller).
 */
@Composable
fun Screensaver(clock: State<Float>, wallpaper: ImageBitmap?, nowPlaying: String?, onCovered: () -> Unit = {}) {
    // Fade in from the dashboard over a couple of seconds, like a slow dim.
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(2500)); onCovered() }
    val start = remember { clock.value }

    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(10_000) } }
    val time = remember(now) { SimpleDateFormat("h:mm", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("EEEE  MMMM d", Locale.getDefault()).format(now).uppercase() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(Color.Black),
    ) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        // Backdrop: the wallpaper, dim and drifting (a slow pan and zoom over about two minutes), or a
        // faint green glow for the animated "Living" wallpaper.
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.value - start
            if (wallpaper != null) {
                val zoom = 1.18f + 0.06f * sin(t * 0.05f)
                val dw = size.width * zoom; val dh = size.height * zoom
                val dx = (size.width - dw) / 2f + (dw - size.width) / 2f * sin(t * 0.031f)
                val dy = (size.height - dh) / 2f + (dh - size.height) / 2f * cos(t * 0.023f)
                drawImage(wallpaper, dstOffset = IntOffset(dx.toInt(), dy.toInt()), dstSize = IntSize(dw.toInt(), dh.toInt()), alpha = 0.28f)
            } else {
                val c = Offset(size.width * (0.5f + 0.3f * sin(t * 0.03f)), size.height * (0.5f + 0.3f * cos(t * 0.021f)))
                drawRect(Brush.radialGradient(listOf(Dash.Moss.copy(alpha = 0.35f), Color.Transparent), c, size.minDimension * 0.8f))
            }
            // Darken the edges so the picture sinks into the black.
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)), center, size.maxDimension * 0.65f))
        }

        // The orb and clock wander together on a slow figure path that covers most of the screen.
        val orbDp = 240.dp
        val blockW = with(density) { 360.dp.toPx() }
        val blockH = with(density) { (orbDp + 130.dp).toPx() }
        Column(
            Modifier.offset {
                val t = clock.value - start
                val x = (w - blockW) * (0.5f + 0.45f * sin(t * 0.043f))
                val y = (h - blockH) * (0.5f + 0.45f * sin(t * 0.029f + 1.3f))
                IntOffset(x.toInt(), y.toInt())
            }.size(360.dp, orbDp + 130.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Orb(clock, 0f, Modifier.size(orbDp).graphicsLayer { alpha = 0.85f })
            // The orb's canvas leaves room below it for its floor glow; tuck the clock up into that space.
            BasicText(time, style = dashText(52.sp, Dash.Text.copy(alpha = 0.8f), bold = true), modifier = Modifier.offset(y = (-34).dp))
            BasicText(date, style = dashText(14.sp, Dash.TextDim.copy(alpha = 0.7f), bold = true, spacing = 3.sp), modifier = Modifier.offset(y = (-34).dp))
            if (nowPlaying != null) {
                BasicText("♪  $nowPlaying", style = dashText(15.sp, Dash.Glow.copy(alpha = 0.75f)), maxLines = 1, modifier = Modifier.offset(y = (-34).dp).padding(top = 6.dp))
            }
        }
    }
}
