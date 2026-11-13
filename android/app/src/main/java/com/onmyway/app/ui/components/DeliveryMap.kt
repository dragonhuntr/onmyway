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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.tan

/**
 * Street map of a delivery. Pickup, drop-off, and the runner are placed from real coordinates.
 * A walking route follows campus paths and shortens as the runner moves toward the next stop.
 * Tiles come straight from OpenStreetMap, drawn by the app, so the map does not depend on a web view.
 */
@Composable
fun DeliveryMap(
    height: Dp,
    pickup: Pair<Double, Double>?,
    pickupTitle: String,
    dropoff: Pair<Double, Double>?,
    dropoffTitle: String,
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
        val from = runner ?: pickup
        val to = if (runner == null) dropoff else if (headingToPickup) pickup else dropoff
        var fullRoute by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
        val requestKey = routeRequestKey(fullRoute, from, to)
        LaunchedEffect(requestKey) {
            if (!needsRoute(fullRoute, from, to)) return@LaunchedEffect
            val fetched = walkingRoute(from, to)
            if (!fetched.isNullOrEmpty()) fullRoute = fetched
        }
        val line = remainingRoute(fullRoute, from, to)
        val routeFits = fullRoute.size >= 2 && meters(fullRoute.last(), to) <= 40
        val framePoints = buildList {
            if (routeFits) addAll(fullRoute)
            add(pickup)
            add(dropoff)
            if (runner != null) add(runner)
        }
        val frame = remember(framePoints, widthPx, heightPx) { mapFrame(framePoints, widthPx, heightPx) }
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
            val projected = line.map { frame.project(it) }
            if (projected.size >= 2) {
                val path = Path()
                path.moveTo(projected.first().x, projected.first().y)
                projected.drop(1).forEach { path.lineTo(it.x, it.y) }
                drawPath(
                    path,
                    color = OmwColors.Brand,
                    style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        MapPin(frame.project(pickup), Color(0xFFE07A2F), pickupTitle)
        MapPin(frame.project(dropoff), OmwColors.Brand, dropoffTitle)
        if (runner != null) MapLabel(runnerTitle, frame.project(runner), filled = true, dot = OmwColors.BrandDeep)
        Text(
            "© OpenStreetMap",
            style = OmwType.Caption2,
            color = OmwColors.InkSecondary,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
        )
    }
}

/** A new route when the next stop changes, or when the runner has left the path by about 45 meters. */
private fun needsRoute(route: List<Pair<Double, Double>>, from: Pair<Double, Double>, to: Pair<Double, Double>): Boolean {
    if (meters(from, to) < 25) return false
    if (route.size < 2) return true
    if (meters(route.last(), to) > 40) return true
    return route.minOf { meters(from, it) } > 45
}

/** Stable while the runner stays on the path, so a location update trims the line without another request. */
private fun routeRequestKey(route: List<Pair<Double, Double>>, from: Pair<Double, Double>, to: Pair<Double, Double>): String {
    val end = "${(to.first * 2_000).toInt()}:${(to.second * 2_000).toInt()}"
    if (!needsRoute(route, from, to)) return "$end|on"
    val start = "${(from.first * 2_000).toInt()}:${(from.second * 2_000).toInt()}"
    return "$end|off|$start"
}

/** Walking path still ahead. It begins at the live position, so it shortens as the runner gets closer. */
private fun remainingRoute(
    route: List<Pair<Double, Double>>,
    from: Pair<Double, Double>,
    to: Pair<Double, Double>,
): List<Pair<Double, Double>> {
    if (route.size < 2 || meters(route.last(), to) > 40) return listOf(from, to)
    val index = route.indices.minBy { meters(from, route[it]) }
    return listOf(from) + route.drop(index)
}

private fun meters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val lat = (a.first - b.first) * 111_320.0
    val lng = (a.second - b.second) * 111_320.0 * cos(Math.toRadians((a.first + b.first) / 2.0))
    return hypot(lat, lng)
}

/** Walking directions along campus paths. Null when the request fails, so the map keeps a straight line. */
private suspend fun walkingRoute(from: Pair<Double, Double>, to: Pair<Double, Double>): List<Pair<Double, Double>>? =
    withContext(Dispatchers.IO) {
        try {
            val connection = URL(
                "https://router.project-osrm.org/route/v1/foot/${from.second},${from.first};${to.second},${to.first}?overview=full&geometries=geojson",
            ).openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "OnMyWay/1.0 (campus delivery)")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            if (connection.responseCode != 200) return@withContext null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (json.optString("code") != "Ok") return@withContext null
            val coordinates = json.getJSONArray("routes").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
            if (coordinates.length() < 2) return@withContext null
            List(coordinates.length()) { index ->
                val pair = coordinates.getJSONArray(index)
                pair.getDouble(1) to pair.getDouble(0)
            }
        } catch (_: Exception) {
            null
        }
    }

/** Colored stop pin. The street map already names the building, so the pin has no second label. */
@Composable
private fun MapPin(at: Offset, dot: Color, label: String) {
    Box(
        Modifier
            .semantics { contentDescription = label }
            .offset { androidx.compose.ui.unit.IntOffset(at.x.toInt(), at.y.toInt()) }
            .layout { measurable, _ ->
                val placeable = measurable.measure(Constraints())
                layout(0, 0) { placeable.place(-placeable.width / 2, -placeable.height / 2) }
            }
            .size(14.dp)
            .background(dot, CircleShape)
            .border(2.dp, Color.White, CircleShape),
    )
}

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
