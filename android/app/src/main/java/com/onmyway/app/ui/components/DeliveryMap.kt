package com.onmyway.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onmyway.app.model.Building
import com.onmyway.app.model.Destination
import com.onmyway.app.model.Place
import com.onmyway.app.ui.theme.OmwColors
import com.onmyway.app.ui.theme.OmwType
import com.onmyway.app.ui.theme.semibold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Street map of a delivery. Pickup, drop-off, and the runner are placed from real coordinates.
 * Tiles come straight from OpenStreetMap, drawn by the app, so the map does not depend on a web view.
 */
@Composable
fun DeliveryMap(
    height: Dp,
    pickup: Pair<Double, Double>?,
    pickupTitle: String,
    dropoff: Pair<Double, Double>?,
    dropoffTitle: String,
    buildings: List<Building> = emptyList(),
    runner: Pair<Double, Double>? = null,
    runnerTitle: String = "Runner",
    headingToPickup: Boolean = true,
) {
    if (pickup == null || dropoff == null) {
        Box(
            Modifier.fillMaxWidth().height(height).background(OmwColors.MapGround),
            contentAlignment = Alignment.Center,
        ) {
            Text("Waiting for the pickup and drop-off locations", style = OmwType.Subheadline, color = OmwColors.InkSecondary)
        }
        return
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(OmwColors.MapGround)
            .clipToBounds(),
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val spots = listOfNotNull(pickup, dropoff, runner)
        val frame = remember(spots, widthPx, heightPx) { mapFrame(spots, widthPx, heightPx) }
        var tiles by remember { mutableStateOf<Map<TileKey, ImageBitmap>>(emptyMap()) }
        LaunchedEffect(frame.keys) {
            tiles = frame.tiles.map { tile ->
                async { tile.key to loadTile(tile.key) }
            }.awaitAll().mapNotNull { (key, bitmap) -> bitmap?.let { key to it } }.toMap()
        }
        Canvas(Modifier.fillMaxSize()) {
            frame.tiles.forEach { tile ->
                tiles[tile.key]?.let { drawImage(it, topLeft = Offset(tile.left, tile.top)) }
            }
            val from = frame.project(runner ?: pickup)
            val to = frame.project(if (runner == null) dropoff else if (headingToPickup) pickup else dropoff)
            drawLine(
                color = OmwColors.Brand,
                start = from,
                end = to,
                strokeWidth = 8f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f)),
            )
        }
        buildings.forEach { building ->
            val point = building.point() ?: return@forEach
            if (sameSpot(point, pickup) || sameSpot(point, dropoff)) return@forEach
            MapLabel(building.name, frame.project(point), filled = false)
        }
        MapLabel(pickupTitle, frame.project(pickup), filled = true, dot = Color(0xFFE07A2F))
        MapLabel(dropoffTitle, frame.project(dropoff), filled = true, dot = OmwColors.Brand)
        if (runner != null) MapLabel(runnerTitle, frame.project(runner), filled = true, dot = OmwColors.BrandDeep)
        Text(
            "© OpenStreetMap",
            style = OmwType.Caption2,
            color = OmwColors.InkSecondary,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
        )
    }
}

private fun sameSpot(a: Pair<Double, Double>, b: Pair<Double, Double>) =
    kotlin.math.abs(a.first - b.first) < 0.0002 && kotlin.math.abs(a.second - b.second) < 0.0002

/** Label whose bottom center sits on the map point. */
@Composable
private fun MapLabel(title: String, at: Offset, filled: Boolean, dot: Color? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset(at.x.toInt(), at.y.toInt()) }
            .layout { measurable, _ ->
                val placeable = measurable.measure(Constraints(maxWidth = 420, maxHeight = 240))
                layout(0, 0) { placeable.place(-placeable.width / 2, -placeable.height) }
            },
    ) {
        Text(
            title,
            style = OmwType.Caption2.semibold,
            color = if (filled) Color.White else OmwColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(if (filled) OmwColors.Brand else Color.White, RoundedCornerShape(8.dp))
                .border(1.dp, if (filled) OmwColors.Brand else OmwColors.Hairline, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        if (dot != null) {
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .size(14.dp)
                    .background(dot, CircleShape)
                    .border(2.dp, Color.White, CircleShape),
            )
        }
    }
}

private data class TileKey(val z: Int, val x: Int, val y: Int)

private data class PlacedTile(val key: TileKey, val left: Float, val top: Float)

private class MapFrame(val tiles: List<PlacedTile>, val project: (Pair<Double, Double>) -> Offset) {
    val keys: List<TileKey> = tiles.map { it.key }
}

private fun mapFrame(points: List<Pair<Double, Double>>, width: Float, height: Float): MapFrame {
    var minLat = points.minOf { it.first }
    var maxLat = points.maxOf { it.first }
    var minLng = points.minOf { it.second }
    var maxLng = points.maxOf { it.second }
    val latPad = (maxLat - minLat).coerceAtLeast(0.0015) * 0.55
    val lngPad = (maxLng - minLng).coerceAtLeast(0.0015) * 0.55
    minLat -= latPad
    maxLat += latPad
    minLng -= lngPad
    maxLng += lngPad

    var zoom = 17
    while (zoom > 13) {
        val spanX = worldX(maxLng, zoom) - worldX(minLng, zoom)
        val spanY = worldY(minLat, zoom) - worldY(maxLat, zoom)
        if (spanX <= width * 0.9f && spanY <= height * 0.9f) break
        zoom--
    }
    val originX = (worldX(minLng, zoom) + worldX(maxLng, zoom)) / 2.0 - width / 2.0
    val originY = (worldY(maxLat, zoom) + worldY(minLat, zoom)) / 2.0 - height / 2.0
    val project = { point: Pair<Double, Double> ->
        Offset(
            (worldX(point.second, zoom) - originX).toFloat(),
            (worldY(point.first, zoom) - originY).toFloat(),
        )
    }
    val x0 = floor(originX / 256.0).toInt()
    val y0 = floor(originY / 256.0).toInt()
    val x1 = floor((originX + width) / 256.0).toInt()
    val y1 = floor((originY + height) / 256.0).toInt()
    val limit = 1 shl zoom
    val tiles = buildList {
        for (x in x0..x1) {
            for (y in y0..y1) {
                if (x !in 0 until limit || y !in 0 until limit) continue
                add(PlacedTile(TileKey(zoom, x, y), left = (x * 256.0 - originX).toFloat(), top = (y * 256.0 - originY).toFloat()))
            }
        }
    }
    return MapFrame(tiles, project)
}

private fun worldX(lng: Double, zoom: Int): Double = (lng + 180.0) / 360.0 * (1 shl zoom) * 256.0

private fun worldY(lat: Double, zoom: Int): Double {
    val rad = Math.toRadians(lat.coerceIn(-85.0, 85.0))
    return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * (1 shl zoom) * 256.0
}

private suspend fun loadTile(key: TileKey): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        val connection = URL("https://tile.openstreetmap.org/${key.z}/${key.x}/${key.y}.png").openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "OnMyWay/1.0 (campus delivery)")
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        if (connection.responseCode != 200) return@withContext null
        connection.inputStream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
    } catch (_: Exception) {
        null
    }
}

fun Place.point(): Pair<Double, Double>? = if (lat != null && lng != null) lat to lng else null

fun Building.point(): Pair<Double, Double>? = if (lat != null && lng != null) lat to lng else null

fun Destination.point(): Pair<Double, Double>? =
    if (lat != null && lng != null) lat to lng else null
