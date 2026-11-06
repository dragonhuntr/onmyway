package com.onmyway.app.ui.components

import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import com.onmyway.app.model.Building
import com.onmyway.app.model.Destination
import com.onmyway.app.model.Place

/**
 * Street map of the current leg. Pickup and drop-off come from campus coordinates.
 * `runner` is the live location, so the customer sees the runner move between the stops.
 * Tiles are OpenStreetMap; no Google Maps key is required.
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
    if (pickup == null || dropoff == null) return
    val labeled = buildings.mapNotNull { building ->
        val lat = building.lat ?: return@mapNotNull null
        val lng = building.lng ?: return@mapNotNull null
        Triple(building.name, lat, lng)
    }
    val page = remember(pickup, dropoff, pickupTitle, dropoffTitle, runnerTitle, headingToPickup, labeled) {
        mapPage(pickup, pickupTitle, dropoff, dropoffTitle, labeled, runner, runnerTitle, headingToPickup)
    }
    key(page) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    loadDataWithBaseURL("https://tile.openstreetmap.org/", page, "text/html", "utf-8", null)
                }
            },
            update = { web ->
                val point = runner ?: return@AndroidView
                web.evaluateJavascript("window.moveRunner && window.moveRunner(${point.first}, ${point.second})", null)
            },
            modifier = Modifier.fillMaxWidth().height(height),
        )
    }
}

private fun mapPage(
    pickup: Pair<Double, Double>,
    pickupTitle: String,
    dropoff: Pair<Double, Double>,
    dropoffTitle: String,
    buildings: List<Triple<String, Double, Double>>,
    runner: Pair<Double, Double>?,
    runnerTitle: String,
    headingToPickup: Boolean,
): String {
    val buildingJs = buildingLabels(buildings, pickup, pickupTitle, dropoff, dropoffTitle)
        .joinToString(", ") { (name, lat, lng) -> "[${js(name)}, $lat, $lng]" }
    val runnerJs = runner?.let {
        "L.circleMarker([${it.first}, ${it.second}], {radius:10,color:'#fff',weight:3,fillColor:'#1f7a4d',fillOpacity:1}).addTo(map).bindTooltip(${js(runnerTitle)}, {permanent:true, direction:'top', offset:[0,-8], className:'runner'});"
    } ?: "null"
    val focus = if (headingToPickup) "pickup" else "dropoff"
    return """
        <!DOCTYPE html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <style>
          html,body,#map{height:100%;margin:0}
          .leaflet-control-attribution{font-size:10px}
          .bldg{background:#fff;border:1px solid #d5d5d5;border-radius:8px;padding:1px 6px;font:600 11px sans-serif;color:#14211a;box-shadow:none;white-space:pre;text-align:center}
          .bldg:before{display:none}
          .runner{background:#1f7a4d;color:#fff;border:0;border-radius:8px;font:600 11px sans-serif}
        </style>
        </head><body><div id="map"></div><script>
        const map = L.map('map', {zoomControl:false});
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom:19, attribution:'OSM'}).addTo(map);
        for (const [name, lat, lng] of [$buildingJs]) {
          L.marker([lat, lng], {interactive:false, opacity:0}).addTo(map)
            .bindTooltip(name, {permanent:true, direction:'center', className:'bldg'});
        }
        const pickup = L.marker([${pickup.first}, ${pickup.second}]).addTo(map).bindTooltip(${js(pickupTitle)}, {permanent:true, direction:'top'});
        const dropoff = L.marker([${dropoff.first}, ${dropoff.second}]).addTo(map).bindTooltip(${js(dropoffTitle)}, {permanent:true, direction:'top'});
        let runner = $runnerJs;
        const line = L.polyline([], {color:'#1f7a4d', weight:4, dashArray:'8 6'}).addTo(map);
        function leg() {
          const a = runner || pickup;
          const b = runner ? $focus : dropoff;
          if (a && b) line.setLatLngs([a.getLatLng(), b.getLatLng()]);
        }
        function frame() {
          const points = [pickup, dropoff, runner].filter(Boolean).map(m => m.getLatLng());
          if (points.length) map.fitBounds(L.latLngBounds(points).pad(0.35));
          leg();
        }
        window.moveRunner = function(lat, lng) {
          if (!runner) runner = L.circleMarker([lat, lng], {radius:10,color:'#fff',weight:3,fillColor:'#1f7a4d',fillOpacity:1}).addTo(map).bindTooltip(${js(runnerTitle)}, {permanent:true, direction:'top', offset:[0,-8], className:'runner'});
          else runner.setLatLng([lat, lng]);
          leg();
        };
        frame();
        </script></body></html>
    """.trimIndent()
}

/** One label per spot. A pin already names its building, and two halls at one point share a chip. */
private fun buildingLabels(
    buildings: List<Triple<String, Double, Double>>,
    pickup: Pair<Double, Double>,
    pickupTitle: String,
    dropoff: Pair<Double, Double>,
    dropoffTitle: String,
): List<Triple<String, Double, Double>> {
    fun near(pin: Pair<Double, Double>, lat: Double, lng: Double) =
        kotlin.math.abs(pin.first - lat) < 0.0002 && kotlin.math.abs(pin.second - lng) < 0.0002

    return buildings
        .groupBy { (kotlin.math.round(it.second * 10_000).toInt() to kotlin.math.round(it.third * 10_000).toInt()) }
        .mapNotNull { (_, spots) ->
            val (_, lat, lng) = spots.first()
            val names = spots.map { it.first }.distinct().filterNot { name ->
                (name == pickupTitle && near(pickup, lat, lng)) || (name == dropoffTitle && near(dropoff, lat, lng))
            }
            if (names.isEmpty()) null else Triple(names.joinToString("\n"), lat, lng)
        }
}

private fun js(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

fun Place.point(): Pair<Double, Double>? = if (lat != null && lng != null) lat to lng else null

fun Destination.point(): Pair<Double, Double>? =
    if (lat != null && lng != null) lat to lng else null
