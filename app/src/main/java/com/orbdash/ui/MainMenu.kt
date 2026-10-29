package com.orbdash.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** One pod on the home list: a menu entry, or an app ([thumb] is then its icon). */
data class MenuItem(val label: String, val sub: String?, val blurb: String, val icon: Ico, val thumb: androidx.compose.ui.graphics.ImageBitmap? = null)

private val PodWidth = 320.dp
private val PodHeight = 52.dp
private const val SPACING_DP = 62f
/** Two pods above and two below the focused one are fully visible (five in all); anything further
 *  fades out within half a slot, which keeps the wheel clear of the clock and the info panel. */
private const val VISIBLE_SLOTS = 2.05f
/** With at least this many items the wheel wraps around, so the focused pod is always in the middle. */
private const val WRAP_AT = 2

/**
 * Home: the orb sits in the left third; menu pods hang off it on an arc to the right,
 * each tethered by a glowing tube. Only Up/Down matter here, which keeps it remote-friendly.
 * [camera] nudges the orb opposite to the backdrop for a little parallax.
 */
@Composable
fun MainMenu(items: List<MenuItem>, index: Int, clock: State<Float>, energy: Float, camera: Offset) {
    // The list scrolls like a wheel: the focused pod always sits level with the orb's centre,
    // so a TV with forty apps works the same as one with four.
    // The scroll position is only ever read inside draw/layout lambdas, never during composition,
    // so scrolling moves what's already on screen instead of rebuilding the whole menu every frame.
    //
    // With 5+ items it wraps around: positions are "virtual" (unbounded) so going from the last item
    // to the first turns the wheel one step instead of spinning all the way back.
    val n = items.size
    val wraps = n >= WRAP_AT
    val wheelHolder = remember { intArrayOf(index) }
    if (wraps) {
        var d = ((index - wheelHolder[0]) % n + n) % n
        if (d > n / 2) d -= n
        wheelHolder[0] += d
    } else {
        wheelHolder[0] = index
    }
    val target = wheelHolder[0]
    fun itemAt(v: Int) = if (wraps) ((v % n) + n) % n else v
    // Up to two pods above and two below. Short lists show fewer rather than repeating an app:
    // 4 items = 1 above + 2 below, 3 = 1 + 1. Long lists keep one extra slot each side to slide in.
    val above = minOf(2, (n - 1) / 2)
    val below = minOf(2, n - 1 - above)
    val slots = when {
        !wraps -> target..target
        n > 5 -> (target - 3)..(target + 3)
        else -> (target - above)..(target + below)
    }
    val posState = animateFloatAsState(target.toFloat(), tween(260), label = "wheel")
    fun fade(v: Int, pos: Float) = 1f - ((abs(v - pos) - VISIBLE_SLOTS) / 0.5f).coerceIn(0f, 1f)
    val reduced = LocalReducedEffects.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val ox = w * (0.29f + camera.x * 0.4f)
        val oy = h * (0.472f + camera.y * 0.4f)
        val orbSize = h * 0.70f
        val orbR = orbSize * 0.36f
        val arcR = orbR + 140.dp

        // Pod anchor point (left-centre of the pill) on a circle around the orb, for scroll position [pos].
        fun anchor(i: Int, pos: Float): Pair<Dp, Dp> {
            val dy = (SPACING_DP * (i - pos)).dp
            val dx = sqrt((arcR.value * arcR.value - dy.value * dy.value).coerceAtLeast(0f)).dp
            return Pair(ox + dx, oy + dy)
        }

        // Tethers first so pods and orb draw over their ends.
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.value
            val o = Offset(ox.toPx(), oy.toPx())
            val pos = posState.value
            for (v in slots) {
                val f = fade(v, pos)
                if (f <= 0f) continue
                val (px, py) = anchor(v, pos)
                val i = itemAt(v)
                val end = Offset(px.toPx() + 4f, py.toPx())
                val a = atan2(end.y - o.y, end.x - o.x)
                val start = Offset(o.x + cos(a) * orbR.toPx() * 0.9f, o.y + sin(a) * orbR.toPx() * 0.9f)
                // Swaying means re-tessellating every tube every frame; slow TVs keep them still,
                // but with the full shading and ribs, which cost little once the shape stops changing.
                val sway = if (reduced) 0f else 10f * sin(t * 0.9f + i)
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    cubicTo(start.x + (end.x - start.x) * 0.55f, start.y + sway, end.x - (end.x - start.x) * 0.45f, end.y - sway, end.x, end.y)
                }
                val sel = v == target
                val width = if (sel) 18f else 12f
                // Shaded tube: dark body, mid tone, then a highlight offset upwards like a lit cylinder.
                drawPath(path, Color.Black.copy(alpha = 0.5f * f), style = Stroke(width + 6f, cap = StrokeCap.Round))
                drawPath(path, Dash.Moss.copy(alpha = f), style = Stroke(width, cap = StrokeCap.Round))
                drawPath(path, (if (sel) Dash.Lime else Dash.Green).copy(alpha = (if (sel) 0.9f else 0.5f) * f), style = Stroke(width * 0.45f, cap = StrokeCap.Round))
                // Ribs make the tube read as organic tubing. A dashed stroke draws them all in one call
                // (thin dashes across the full tube width) instead of dozens of separate lines.
                drawPath(path, Color.Black.copy(alpha = 0.35f * f), style = Stroke(width, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(1.5f, 14.5f))))
                drawPath(path, Color.White.copy(alpha = (if (sel) 0.55f else 0.18f) * f), style = Stroke(1.5f, cap = StrokeCap.Round))
                if (sel) {
                    // A bead of light travelling from the orb out to the focused pod.
                    val pm = PathMeasure().apply { setPath(path, false) }
                    val pos = pm.getPosition(pm.length * ((t * 0.9f) % 1f))
                    drawCircle(Brush.radialGradient(listOf(Color.White, Dash.Glow.copy(alpha = 0f)), pos, 18f), 18f, pos)
                }
                // Socket collar where the tube plugs into the pod.
                drawCircle(Dash.Deep.copy(alpha = f), width * 0.9f, end)
                drawCircle((if (sel) Dash.Glow else Dash.PanelEdge).copy(alpha = f), width * 0.9f, end, style = Stroke(2.5f))
            }
        }

        Orb(clock, energy, Modifier.size(orbSize * 1.5f).offset(ox - orbSize * 0.75f, oy - orbSize * 0.75f))

        // Only pods near the focus are composed; their position and fade update in the layout/draw phase.
        for (v in slots) key(v) {
            val item = items[itemAt(v)]
            JellyPill(
                text = item.label,
                sub = item.sub,
                selected = v == target,
                height = PodHeight,
                icon = item.icon.takeIf { item.thumb == null },
                thumb = item.thumb,
                depth = (abs(v - target) / 3f).coerceAtMost(1f),
                modifier = Modifier
                    .offset {
                        val (px, py) = anchor(v, posState.value)
                        IntOffset(px.roundToPx(), (py - PodHeight / 2).roundToPx())
                    }
                    .graphicsLayer { alpha = fade(v, posState.value) }
                    .width(PodWidth).height(PodHeight),
            )
        }

        // "More this way" arrows above and below the wheel.
        val arrowX = ox + arcR + PodWidth / 2 - 60.dp
        // A wrapping wheel never ends, so the "more" arrows only show on short, non-wrapping lists.
        if (!wraps && index > 1) MoreArrow(up = true, Modifier.offset(arrowX, oy - (SPACING_DP * (VISIBLE_SLOTS + 0.75f)).dp))
        if (!wraps && items.size - 1 - index > 1) MoreArrow(up = false, Modifier.offset(arrowX, oy + (SPACING_DP * (VISIBLE_SLOTS + 0.55f)).dp))

        StatusCluster(Modifier.align(Alignment.TopEnd).padding(top = 26.dp, end = 48.dp))

        // Plain-language description of the focused pod, so nobody has to guess what it does.
        items.getOrNull(index)?.let {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 28.dp)
                    .width(540.dp)
                    .bracketFrame(Dash.PanelEdge, len = 12.dp, gap = 4.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0x99062206), Color(0x55062206))), RoundedCornerShape(6.dp))
                    .border(1.dp, Dash.PanelEdge.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val thumb = it.thumb
                if (thumb != null) androidx.compose.foundation.Image(thumb, null, Modifier.size(38.dp)) else IconView(it.icon, Modifier.size(34.dp), Dash.Glow)
                Spacer(Modifier.width(14.dp))
                Column {
                    BasicText("${it.label.uppercase()}   ·   ${index + 1} OF ${items.size}", style = dashText(15.sp, Dash.Glow, bold = true, spacing = 3.sp), maxLines = 1)
                    BasicText(it.blurb, style = dashText(18.sp, Dash.Text), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
        Legend(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 30.dp), okLabel = "OPEN", backLabel = null)
    }
}

@Composable
private fun MoreArrow(up: Boolean, modifier: Modifier) {
    Canvas(modifier.size(width = 40.dp, height = 16.dp)) {
        val y0 = if (up) size.height else 0f
        val y1 = if (up) 0f else size.height
        drawPath(
            Path().apply { moveTo(0f, y0); lineTo(size.width / 2, y1); lineTo(size.width, y0); close() },
            Dash.Glow.copy(alpha = 0.8f),
        )
    }
}
