package com.onmyway.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Sends the runner's location to the server while this is composed (a delivery is on screen). */
@Composable
fun ReportLocationWhileVisible(report: (latitude: Double, longitude: Double) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasLocationPermission()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        granted = results.values.any { it }
    }
    LaunchedEffect(Unit) {
        if (!granted) {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    val latestReport by rememberUpdatedState(report)
    DisposableEffect(granted) {
        val manager = context.getSystemService(LocationManager::class.java)
        if (!granted || manager == null) return@DisposableEffect onDispose {}

        var lastSent = 0L
        val listener = LocationListener { location ->
            // Every 15 seconds is enough for a walking pace.
            val now = SystemClock.elapsedRealtime()
            if (lastSent == 0L || now - lastSent > 15_000) {
                lastSent = now
                latestReport(location.latitude, location.longitude)
            }
        }
        try {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter(manager::isProviderEnabled)
                .forEach { manager.requestLocationUpdates(it, 5_000L, 10f, listener, Looper.getMainLooper()) }
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the request.
        }
        onDispose { manager.removeUpdates(listener) }
    }
}

private fun Context.hasLocationPermission(): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
