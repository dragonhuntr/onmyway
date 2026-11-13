package com.onmyway.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onmyway.app.ui.theme.OmwColors
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Maps campus building and dining names at Penn State Behrend (Erie, PA) to Lat/Lng coordinates.
 */
fun getBuildingCoords(name: String): Pair<Double, Double> = when {
    // Dining & Cafés
    name.contains("Bruno", ignoreCase = true) -> 42.1189 to -79.9882
    name.contains("Dobbins", ignoreCase = true) -> 42.1205 to -79.9858
    name.contains("Clark", ignoreCase = true) -> 42.1168 to -79.9870
    name.contains("Paw", ignoreCase = true) -> 42.1192 to -79.9878

    // Residence Halls
    name.contains("Senat", ignoreCase = true) -> 42.1210 to -79.9865
    name.contains("Niagara", ignoreCase = true) -> 42.1215 to -79.9850
    name.contains("Lawrence", ignoreCase = true) -> 42.1200 to -79.9855
    name.contains("Perry", ignoreCase = true) -> 42.1208 to -79.9845
    name.contains("Trippe", ignoreCase = true) -> 42.1198 to -79.9875
    name.contains("Porcupine", ignoreCase = true) -> 42.1198 to -79.9875
    name.contains("Ohio", ignoreCase = true) -> 42.1202 to -79.9860
    name.contains("Almy", ignoreCase = true) -> 42.1206 to -79.9852

    // Academic & Union Buildings
    name.contains("Reed", ignoreCase = true) || name.contains("Union", ignoreCase = true) -> 42.1188 to -79.9883
    name.contains("Burke", ignoreCase = true) -> 42.1166 to -79.9872
    name.contains("Hammermill", ignoreCase = true) -> 42.1180 to -79.9890
    name.contains("Kochel", ignoreCase = true) -> 42.1182 to -79.9875
    name.contains("Erie", ignoreCase = true) -> 42.1195 to -79.9895
    name.contains("Zurn", ignoreCase = true) || name.contains("Fasenmyer", ignoreCase = true) -> 42.1172 to -79.9882
    name.contains("Behrend", ignoreCase = true) || name.contains("Science", ignoreCase = true) -> 42.1185 to -79.9868

    // Default Penn State Behrend Campus Center Coordinates
    else -> 42.1190 to -79.9880
}

/**
 * OpenStreetMap view using osmdroid displaying Drop-off Point, Runner Location, and Pickup Point.
 *
 * @param latitude Latitude of the drop-off destination
 * @param longitude Longitude of the drop-off destination
 * @param locationName Display title for the drop-off point (e.g. "Senat Hall, Rm 201")
 * @param height Height of the map component
 * @param pickupLat Optional latitude of the restaurant/pickup location
 * @param pickupLng Optional longitude of the restaurant/pickup location
 * @param pickupName Optional title for the pickup location (e.g. "Bruno's Cafe")
 * @param runnerLat Optional latitude of the runner's current position
 * @param runnerLng Optional longitude of the runner's current position
 * @param runnerName Optional name/label for the runner (e.g. "Evan B.")
 * @param zoomLevel Initial zoom level
 */
@Composable
fun OsmDeliveryMap(
    latitude: Double,
    longitude: Double,
    locationName: String,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
    pickupLat: Double? = null,
    pickupLng: Double? = null,
    pickupName: String? = null,
    runnerLat: Double? = null,
    runnerLng: Double? = null,
    runnerName: String? = null,
    zoomLevel: Double = 17.5,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Set OSM User-Agent configuration
    remember {
        Configuration.getInstance().userAgentValue = context.packageName
        true
    }

    val deliveryPoint = remember(latitude, longitude) { GeoPoint(latitude, longitude) }
    val pickupPoint = remember(pickupLat, pickupLng) {
        if (pickupLat != null && pickupLng != null) {
            GeoPoint(pickupLat, pickupLng)
        } else {
            null
        }
    }
    val runnerPoint = remember(runnerLat, runnerLng) {
        if (runnerLat != null && runnerLng != null) {
            GeoPoint(runnerLat, runnerLng)
        } else {
            null
        }
    }

    var mapViewRef: MapView? = null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(OmwColors.MapGround),
    ) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    mapViewRef = this
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(zoomLevel)
                    controller.setCenter(deliveryPoint)

                    val allPoints = mutableListOf(deliveryPoint)

                    // 1. Drop-off Point Marker
                    val dropoffMarker = Marker(this).apply {
                        position = deliveryPoint
                        title = "Drop-off: $locationName"
                        snippet = "Delivery Location"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    overlays.add(dropoffMarker)

                    // 2. Pickup Location Marker (if provided)
                    pickupPoint?.let { pPoint ->
                        val pickupMarker = Marker(this).apply {
                            position = pPoint
                            title = "Pickup: ${pickupName ?: "Restaurant"}"
                            snippet = "Food Pickup Point"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        overlays.add(pickupMarker)
                        allPoints.add(pPoint)
                    }

                    // 3. Runner Location Marker (if provided)
                    runnerPoint?.let { rPoint ->
                        val runnerMarker = Marker(this).apply {
                            position = rPoint
                            title = "Runner: ${runnerName ?: "On the way"}"
                            snippet = "Current Runner Location"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        }
                        overlays.add(runnerMarker)
                        allPoints.add(rPoint)
                    }

                    // Draw connecting route line (Pickup -> Runner -> Drop-off, or Pickup -> Drop-off)
                    val routePoints = mutableListOf<GeoPoint>()
                    pickupPoint?.let { routePoints.add(it) }
                    runnerPoint?.let { routePoints.add(it) }
                    routePoints.add(deliveryPoint)

                    if (routePoints.size >= 2) {
                        val routeLine = Polyline().apply {
                            setPoints(routePoints)
                            outlinePaint.color = android.graphics.Color.rgb(31, 107, 74) // OmwColors.Brand
                            outlinePaint.strokeWidth = 10f
                        }
                        overlays.add(routeLine)
                    }

                    // Adjust zoom and center to bound all active pins
                    if (allPoints.size > 1) {
                        post {
                            val boundingBox = BoundingBox.fromGeoPoints(ArrayList(allPoints))
                            zoomToBoundingBox(boundingBox.increaseByScale(1.35f), true)
                        }
                    }
                }
            },
            update = { mapView ->
                mapView.controller.setCenter(deliveryPoint)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    // Connect MapView to Android Lifecycle to handle pause/resume cleanly
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
