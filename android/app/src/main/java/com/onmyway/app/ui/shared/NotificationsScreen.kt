package com.onmyway.app.ui.shared

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.onmyway.app.model.AppModel
import com.onmyway.app.model.AppNotification
import com.onmyway.app.ui.components.EmptyState
import com.onmyway.app.ui.components.RefreshableColumn
import com.onmyway.app.ui.components.TopBar
import com.onmyway.app.ui.theme.OmwColors
import com.onmyway.app.ui.theme.OmwType
import com.onmyway.app.ui.theme.card
import com.onmyway.app.ui.theme.semibold

/** The bell on Home — order updates, newest first. Tapping one opens Orders (customers) or Run (runners). */
@Composable
fun NotificationsScreen(model: AppModel) {
    BackHandler { model.showingNotifications = false }

    LaunchedEffect(Unit) {
        model.loadNotifications()
        model.markNotificationsRead()
    }

    Column(Modifier.fillMaxSize().background(OmwColors.Canvas)) {
        TopBar("Notifications", onBack = { model.showingNotifications = false })
        RefreshableColumn(
            onRefresh = model::loadNotifications,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f).navigationBarsPadding(),
        ) {
            if (model.notifications.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Notifications,
                    title = "No notifications yet",
                    message = "Updates on your orders show up here.",
                    modifier = Modifier.padding(top = 80.dp),
                )
            } else {
                Spacer(Modifier.size(10.dp))
                model.notifications.forEach { notification ->
                    NotificationRow(notification) { model.openFromPush(notification.kind) }
                }
                Spacer(Modifier.size(10.dp))
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .card(padding = 14.dp)
            .semantics(mergeDescendants = true) { if (!notification.read) stateDescription = "Unread" },
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .background(if (notification.read) Color.Transparent else OmwColors.Brand, CircleShape),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(notification.title, style = OmwType.Subheadline.semibold, color = OmwColors.Ink, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(
                    DateUtils.getRelativeTimeSpanString(notification.createdAt.toEpochMilli()).toString(),
                    style = OmwType.Caption,
                    color = OmwColors.InkSecondary,
                )
            }
            Text(notification.body, style = OmwType.Footnote, color = OmwColors.InkSecondary)
        }
    }
}
