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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class MenuItem(val label: String, val sub: String?, val blurb: String, val icon: Ico)

private val PodWidth = 300.dp
private val PodHeight = 54.dp
/** Vertical band the pod column must stay inside: below the status row, above the info panel. */
private const val BAND_DP = 370f

/**
 * Home: the orb sits in the left third; menu pods hang off it on an arc to the right,
 * each tethered by a glowing tube. Only Up/Down matter here, which keeps it remote-friendly.
 * [camera] nudges the orb opposite to the backdrop for a little parallax.
 */
@Composable
fun MainMenu(items: List<MenuItem>, index: Int, clock: State<Float>, energy: Float, camera: Offset) {
    // More pods pack tighter; whatever room is left over is how far the column may glide
    // to bring the focused pod towards the orb's eye line.
    val gaps = (items.size - 1).coerceAtLeast(1)
    val spacing = minOf(70f, (BAND_DP - PodHeight.value - 6f) / gaps)
    val slack = BAND_DP - (gaps * spacing + PodHeight.value)
    val glideRate = (slack / (gaps * spacing)).coerceIn(0f, 0.22f)
    val glide by animateFloatAsState(-(index - (items.size - 1) / 2f) * glideRate, tween(320), label = "glide")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val ox = w * (0.29f + camera.x * 0.4f)
        val oy = h * (0.472f + camera.y * 0.4f)
        val orbSize = h * 0.70f
        val orbR = orbSize * 0.36f
        val arcR = orbR + 140.dp

        // Pod anchor points (left-centre of each pill) on a circle around the orb.
        val pods = items.indices.map { i ->
            val dy = (spacing * (i - (items.size - 1) / 2f + glide)).dp
            val dx = sqrt((arcR.value * arcR.value - dy.value * dy.value).coerceAtLeast(0f)).dp
            Pair(ox + dx, oy + dy)
        }

        // Tethers first so pods and orb draw over their ends.
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.value
            val o = Offset(ox.toPx(), oy.toPx())
            pods.forEachIndexed { i, (px, py) ->
                val end = Offset(px.toPx() + 4f, py.toPx())
                val a = atan2(end.y - o.y, end.x - o.x)
                val start = Offset(o.x + cos(a) * orbR.toPx() * 0.9f, o.y + sin(a) * orbR.toPx() * 0.9f)
                val sway = 10f * sin(t * 0.9f + i)
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    cubicTo(start.x + (end.x - start.x) * 0.55f, start.y + sway, end.x - (end.x - start.x) * 0.45f, end.y - sway, end.x, end.y)
                }
                val sel = i == index
                val width = if (sel) 18f else 12f
                // Shaded tube: dark body, mid tone, then a highlight offset upwards like a lit cylinder.
                drawPath(path, Color.Black.copy(alpha = 0.5f), style = Stroke(width + 6f, cap = StrokeCap.Round))
                drawPath(path, Dash.Moss, style = Stroke(width, cap = StrokeCap.Round))
                drawPath(path, (if (sel) Dash.Lime else Dash.Green).copy(alpha = if (sel) 0.9f else 0.5f), style = Stroke(width * 0.45f, cap = StrokeCap.Round))
                val pm = PathMeasure().apply { setPath(path, false) }
                // Ribs every few pixels make the tube read as organic tubing rather than a line.
                var d = 14f
                while (d < pm.length - 10f) {
                    val p = pm.getPosition(d)
                    val tan = pm.getTangent(d)
                    val n = Offset(-tan.y, tan.x) * (width * 0.5f)
                    drawLine(Color.Black.copy(alpha = 0.35f), p - n, p + n, 1.5f)
                    d += 16f
                }
                drawPath(path, Color.White.copy(alpha = if (sel) 0.55f else 0.18f), style = Stroke(1.5f, cap = StrokeCap.Round))
                if (sel) {
                    // A bead of light travelling from the orb out to the focused pod.
                    val pos = pm.getPosition(pm.length * ((t * 0.9f) % 1f))
                    drawCircle(Brush.radialGradient(listOf(Color.White, Dash.Glow.copy(alpha = 0f)), pos, 18f), 18f, pos)
                }
                // Socket collar where the tube plugs into the pod.
                drawCircle(Dash.Deep, width * 0.9f, end)
                drawCircle((if (sel) Dash.Glow else Dash.PanelEdge), width * 0.9f, end, style = Stroke(2.5f))
            }
        }

        Orb(clock, energy, Modifier.size(orbSize).offset(ox - orbSize / 2, oy - orbSize / 2))

        items.forEachIndexed { i, item ->
            val (px, py) = pods[i]
            JellyPill(
                text = item.label,
                sub = item.sub,
                selected = i == index,
                height = PodHeight,
                icon = item.icon,
                depth = (abs(i - index) / 3f).coerceAtMost(1f),
                modifier = Modifier.offset(px, py - PodHeight / 2).width(PodWidth).height(PodHeight),
            )
        }

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
                IconView(it.icon, Modifier.size(34.dp), Dash.Glow)
                Spacer(Modifier.width(14.dp))
                Column {
                    BasicText(it.label, style = dashText(15.sp, Dash.Glow, bold = true, spacing = 3.sp))
                    BasicText(it.blurb, style = dashText(18.sp, Dash.Text), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
        Legend(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 30.dp), okLabel = "OPEN", backLabel = null)
    }
}
