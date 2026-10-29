package com.orbdash.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/** A row in any sub-screen: an app, a setting, or a call to action. */
data class Entry(
    val title: String,
    val detail: String,
    /** Big picture for the detail panel: an app banner/icon or a wallpaper preview. */
    val art: ImageBitmap? = null,
    val artIsBanner: Boolean = false,
    /** Small picture inside the pill (app icon); falls back to [icon]. */
    val thumb: ImageBitmap? = null,
    val icon: Ico? = null,
    val value: String? = null,
    /** Label on the detail panel's tab, e.g. "APP INFO". */
    val kind: String = "",
    /** Key/value lines for the detail panel, computed only when this row is focused. */
    val readouts: () -> List<Pair<String, String>> = { emptyList() },
    val action: () -> Unit,
)

private const val VISIBLE_ROWS = 6

/**
 * Sub-screen layout from the old Music/Settings menus: title bar top-left, a single vertical
 * list of pills on the left, and a big detail panel on the right for whatever is focused.
 */
@Composable
fun ListScreen(title: String, subtitle: String?, entries: List<Entry>, index: Int, clock: State<Float>) {
    val listState = rememberLazyListState()
    LaunchedEffect(index) { listState.animateScrollToItem((index - 2).coerceAtLeast(0)) }

    Box(Modifier.fillMaxSize()) {
        Header(title, subtitle, Modifier.padding(start = 48.dp, top = 28.dp))
        StatusCluster(Modifier.align(Alignment.TopEnd).padding(top = 26.dp, end = 48.dp))

        ScrollTrack(index, entries.size, Modifier.offset(x = 30.dp, y = 140.dp).width(12.dp).height(300.dp))

        LazyColumn(
            state = listState,
            userScrollEnabled = false,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 18.dp, horizontal = 20.dp),
            modifier = Modifier
                .offset(x = 44.dp, y = 122.dp)
                .width(420.dp)
                .height(340.dp)
                // Rows dissolve at the top and bottom edges instead of being sliced off.
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.07f to Color.Black, 0.9f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                },
        ) {
            itemsIndexed(entries) { i, e ->
                JellyPill(
                    e.title, i == index, Modifier.fillMaxWidth().height(48.dp),
                    height = 48.dp, textSize = 21.sp, sub = e.value, icon = e.icon, thumb = e.thumb,
                )
            }
        }

        entries.getOrNull(index)?.let { e ->
            DetailPanel(e, index, entries.size, clock, Modifier.offset(x = 500.dp, y = 110.dp).width(412.dp))
        }

        Legend(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 30.dp))
    }
}

/** A thin rail with a glowing bead for "where am I in this list", plus up/down arrows when there's more. */
@Composable
private fun ScrollTrack(index: Int, count: Int, modifier: Modifier) {
    if (count <= VISIBLE_ROWS) return
    val f by animateFloatAsState(index / (count - 1f).coerceAtLeast(1f), tween(220), label = "track")
    Canvas(modifier) {
        val x = size.width / 2
        val top = 14f; val bottom = size.height - 14f
        drawLine(Dash.Moss, Offset(x, top), Offset(x, bottom), 4f, StrokeCap.Round)
        drawLine(Dash.PanelEdge.copy(alpha = 0.5f), Offset(x, top), Offset(x, bottom), 1.5f, StrokeCap.Round)
        val y = top + (bottom - top) * f
        drawCircle(Brush.radialGradient(listOf(Dash.Glow.copy(alpha = 0.7f), Color.Transparent), Offset(x, y), 18f), 18f, Offset(x, y))
        drawCircle(Dash.Core, 5f, Offset(x, y))
        val arrow = 6f
        if (index > 0) drawLine(Dash.TextDim, Offset(x - arrow, 8f), Offset(x, 2f), 2f, StrokeCap.Round).also { drawLine(Dash.TextDim, Offset(x + arrow, 8f), Offset(x, 2f), 2f, StrokeCap.Round) }
        if (index < count - 1) drawLine(Dash.TextDim, Offset(x - arrow, size.height - 8f), Offset(x, size.height - 2f), 2f, StrokeCap.Round).also { drawLine(Dash.TextDim, Offset(x + arrow, size.height - 8f), Offset(x, size.height - 2f), 2f, StrokeCap.Round) }
    }
}

@Composable
private fun DetailPanel(e: Entry, index: Int, count: Int, clock: State<Float>, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    val readouts = remember(e) { e.readouts() }
    Column(modifier.bracketFrame()) {
        if (e.kind.isNotEmpty()) {
            // Tab sitting on top of the panel.
            Box(
                Modifier
                    .padding(start = 18.dp)
                    .background(Dash.PanelEdge, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .padding(horizontal = 12.dp, vertical = 2.dp),
            ) { BasicText(e.kind, style = dashText(12.sp, Dash.Deep, bold = true, spacing = 3.sp)) }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0x9916420E), Dash.PanelFill, Color(0xCC031203))), shape)
                .border(1.5.dp, Dash.PanelEdge, shape)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            ArtWindow(e, clock)
            BasicText(e.title, style = dashText(26.sp, Dash.Core, bold = true), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
            BasicText(e.detail, style = dashText(16.sp, Dash.Text), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            if (readouts.isNotEmpty()) {
                Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Dash.PanelEdge, Color.Transparent))))
                for ((k, v) in readouts.take(4)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(k, style = dashText(13.sp, Dash.TextDim, bold = true, spacing = 2.sp), modifier = Modifier.width(118.dp))
                        BasicText(v, style = dashText(15.sp, Dash.Text, bold = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            BasicText("${index + 1} / $count", style = dashText(13.sp, Dash.TextDim, spacing = 2.sp), modifier = Modifier.align(Alignment.End).padding(top = 6.dp))
        }
    }
}

/** The picture window: banner, icon on a glow, or a big line icon with a slowly rotating ring. */
@Composable
private fun ArtWindow(e: Entry, clock: State<Float>) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.radialGradient(listOf(Dash.Green.copy(alpha = 0.55f), Dash.Deep, Color(0xFF020A02))))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val art = e.art
        when {
            art != null && e.artIsBanner -> Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            art != null -> Image(art, null, Modifier.size(96.dp))
            else -> {
                Canvas(Modifier.size(100.dp)) {
                    val t = clock.value
                    drawCircle(Dash.PanelEdge.copy(alpha = 0.5f), size.minDimension * 0.46f, style = Stroke(2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f), t * 12f)))
                    drawCircle(Brush.radialGradient(listOf(Dash.Lime.copy(alpha = 0.3f + 0.1f * sin(t * 2f)), Color.Transparent)), size.minDimension * 0.42f)
                }
                IconView(e.icon ?: Ico.INFO, Modifier.size(64.dp), Dash.Glow)
            }
        }
        // Glass sheen over the window.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.12f), 0.45f to Color.Transparent)))
    }
}
