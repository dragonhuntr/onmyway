package com.onmyway.app

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.onmyway.app.model.AppModel
import com.onmyway.app.model.Push
import com.onmyway.app.ui.App
import com.onmyway.app.ui.theme.OnMyWayTheme

class MainActivity : ComponentActivity() {
    private val model: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is light-only, like the iOS build.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        Push.createChannel(this)
        if (savedInstanceState == null) openFromPush(intent)
        setContent {
            // Ask once someone is signed in, so the prompt comes with context.
            val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            LaunchedEffect(model.session?.id) {
                if (model.session != null && Build.VERSION.SDK_INT >= 33 && Push.isConfigured(this@MainActivity)) {
                    askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            OnMyWayTheme {
                App(model)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFromPush(intent)
    }

    private fun openFromPush(intent: Intent) {
        model.openFromPush(intent.getStringExtra(Push.EXTRA_KIND))
    }
}
