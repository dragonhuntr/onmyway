package com.onmyway.app.model

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.onmyway.app.MainActivity
import com.onmyway.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Push notifications through Firebase Cloud Messaging. Off until `app/google-services.json` is added. */
object Push {
    const val CHANNEL_ID = "orders"
    /** Intent extras a notification tap carries, set by the Worker in `api/src/push.ts`. */
    const val EXTRA_KIND = "kind"
    const val EXTRA_ORDER_ID = "orderId"

    fun isConfigured(context: Context) = FirebaseApp.getApps(context).isNotEmpty()

    fun createChannel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Orders and messages")
                .setDescription("Runner updates on your order and new chat messages")
                .build(),
        )
    }

    /** Sends this install's push token to the Worker for the signed-in session. */
    suspend fun register(context: Context, sessionToken: String, pushToken: String? = null) {
        val token = pushToken ?: currentToken(context) ?: return
        runCatching {
            Api.send<Empty>("POST", "devices", mapOf("token" to token, "platform" to "android"), token = sessionToken)
        }
    }

    private suspend fun currentToken(context: Context): String? {
        if (!isConfigured(context)) return null
        return suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }
}

/**
 * Receives push tokens and, while the app is open, notifications. Firebase shows notifications
 * itself when the app is in the background.
 */
class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val session = AppModel.savedToken(this) ?: return
        CoroutineScope(Dispatchers.IO).launch { Push.register(this@PushService, session, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val tap = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(Push.EXTRA_KIND, message.data[Push.EXTRA_KIND])
            .putExtra(Push.EXTRA_ORDER_ID, message.data[Push.EXTRA_ORDER_ID])
        val id = message.messageId.hashCode()
        NotificationManagerCompat.from(this).notify(
            id,
            NotificationCompat.Builder(this, Push.CHANNEL_ID)
                .setSmallIcon(R.drawable.footprints_white)
                .setContentTitle(notification.title)
                .setContentText(notification.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(this, id, tap, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                .build(),
        )
    }
}
