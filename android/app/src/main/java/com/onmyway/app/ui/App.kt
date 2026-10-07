package com.onmyway.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.onmyway.app.model.AppModel
import com.onmyway.app.model.AppTab
import com.onmyway.app.ui.auth.AuthFlow
import com.onmyway.app.ui.earnings.EarningsScreen
import com.onmyway.app.ui.order.DeliveredScreen
import com.onmyway.app.ui.order.HomeScreen
import com.onmyway.app.ui.order.OrderFlow
import com.onmyway.app.ui.order.OrdersTab
import com.onmyway.app.ui.run.RunTab
import com.onmyway.app.ui.shared.ChatScreen
import com.onmyway.app.ui.theme.OmwColors

@Composable
fun App(model: AppModel) {
    when {
        model.isRestoringSession -> Box(
            Modifier.fillMaxSize().background(OmwColors.Canvas),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = OmwColors.InkSecondary) }
        model.session == null -> AuthFlow(model)
        else -> MainTabs(model)
    }
}

private val tabs = listOf(
    Triple(AppTab.Home, "Home", Icons.Outlined.Home),
    Triple(AppTab.Orders, "Orders", Icons.AutoMirrored.Outlined.ReceiptLong),
    Triple(AppTab.Run, "Run", Icons.Outlined.Bolt),
    Triple(AppTab.Earnings, "Earnings", Icons.Outlined.AccountBalanceWallet),
)

@Composable
private fun MainTabs(model: AppModel) {
    model.notice?.let { notice ->
        AlertDialog(
            onDismissRequest = { model.notice = null },
            confirmButton = { TextButton({ model.notice = null }) { Text("OK") } },
            title = { Text("Order closed") },
            text = { Text(notice) },
            containerColor = Color.White,
        )
    }

    // Full-screen covers sit above the tab bar, like iOS `fullScreenCover`.
    model.chat?.let { return ChatScreen(model, it) }
    model.deliveredOrder?.let { return DeliveredScreen(model, it) }
    model.orderingFrom?.let { return OrderFlow(model, it) }

    val showsTabBar = !(model.selectedTab == AppTab.Run && model.runPath.isNotEmpty())
    Column(Modifier.fillMaxSize().background(OmwColors.Canvas)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (model.selectedTab) {
                AppTab.Home -> HomeScreen(model)
                AppTab.Orders -> OrdersTab(model)
                AppTab.Run -> RunTab(model)
                AppTab.Earnings -> EarningsScreen(model)
            }
        }
        if (showsTabBar) TabBar(model)
    }
}

@Composable
private fun TabBar(model: AppModel) {
    Column {
        HorizontalDivider(color = OmwColors.Hairline)
        NavigationBar(containerColor = Color.White) {
            tabs.forEach { (tab, label, icon) ->
                TabItem(model, tab, label, icon)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(
    model: AppModel,
    tab: AppTab,
    label: String,
    icon: ImageVector,
) {
    NavigationBarItem(
        selected = model.selectedTab == tab,
        onClick = { model.selectedTab = tab },
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = OmwColors.Brand,
            selectedTextColor = OmwColors.Brand,
            indicatorColor = OmwColors.BrandSoft,
            unselectedIconColor = OmwColors.InkSecondary,
            unselectedTextColor = OmwColors.InkSecondary,
        ),
    )
}
