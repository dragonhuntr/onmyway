package com.onmyway.app.ui.earnings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onmyway.app.R
import com.onmyway.app.model.ApiException
import com.onmyway.app.model.AppModel
import com.onmyway.app.model.DailyEarning
import com.onmyway.app.model.Earnings
import com.onmyway.app.model.EarningsPeriod
import com.onmyway.app.model.usd
import com.onmyway.app.ui.components.LargeTitle
import com.onmyway.app.ui.components.RefreshableColumn
import com.onmyway.app.ui.components.openUrl
import com.onmyway.app.ui.theme.CompactButton
import com.onmyway.app.ui.theme.Eyebrow
import com.onmyway.app.ui.theme.IconBadge
import com.onmyway.app.ui.theme.OmwColors
import com.onmyway.app.ui.theme.OmwType
import com.onmyway.app.ui.theme.bold
import com.onmyway.app.ui.theme.card
import com.onmyway.app.ui.theme.medium
import com.onmyway.app.ui.theme.semibold
import com.onmyway.app.ui.theme.usd
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale

/**
 * 10_Earnings — weekly runner earnings, stats, cash out, and recent deliveries.
 *
 * Rebuilt from the Figma layer structure (the design-context export for this frame was not
 * available), so colors and icons follow the other screens' tokens.
 */
@Composable
fun EarningsScreen(model: AppModel) {
    var period by rememberSaveable { mutableStateOf(EarningsPeriod.ThisWeek) }
    var isCashingOut by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val summary = model.earnings[period]
    val days = model.earnings(period)

    suspend fun load() {
        coroutineScope {
            launch { model.loadEarnings(period) }
            launch { model.loadDeliveries() }
        }
    }

    LaunchedEffect(period) { load() }

    /** Opens Stripe onboarding the first time; after that, pays the balance out. */
    fun cashOut() = scope.launch {
        isCashingOut = true
        errorMessage = null
        try {
            val onboarding = model.cashOut()
            if (onboarding != null) context.openUrl(onboarding) else model.loadEarnings(period)
        } catch (e: ApiException) {
            errorMessage = e.message
        } finally {
            isCashingOut = false
        }
    }

    RefreshableColumn(
        onRefresh = ::load,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().background(OmwColors.Canvas).statusBarsPadding(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                LargeTitle("Earnings", Modifier.weight(1f))
                PeriodMenu(period) { period = it }
            }
            WeeklyTotal(period, summary, days)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("${summary?.deliveries ?: 0}", "deliveries", Modifier.weight(1f))
                Stat(walking(summary?.walkingMinutes ?: 0), "walking", Modifier.weight(1f))
                Stat((summary?.averageCents ?: 0).usd, "avg per trip", Modifier.weight(1f))
            }
            CashOut(
                model.cashOutBalanceCents,
                caption = errorMessage ?: if (summary?.payoutsEnabled == false) {
                    "Set up payouts with Stripe to cash out"
                } else {
                    "To your bank via Stripe · no fee"
                },
                enabled = model.cashOutBalanceCents != 0 && !isCashingOut,
                onCashOut = ::cashOut,
            )
            Recent(model)
        }
    }
}

/** "1h 40m" */
private fun walking(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

/** "SEP 7 – SEP 13" */
private fun weekLabel(period: EarningsPeriod, summary: Earnings?): String {
    summary ?: return period.label.uppercase()
    val format = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    return try {
        "${LocalDate.parse(summary.start).format(format)} – ${LocalDate.parse(summary.end).format(format)}".uppercase()
    } catch (e: DateTimeParseException) {
        period.label.uppercase()
    }
}

@Composable
private fun PeriodMenu(period: EarningsPeriod, onSelect: (EarningsPeriod) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.DropdownList) { expanded = true }
                .padding(horizontal = 6.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Outlined.Schedule, null, tint = OmwColors.Ink, modifier = Modifier.size(16.dp))
            Text(period.label, style = OmwType.Footnote.medium, color = OmwColors.Ink)
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = OmwColors.Ink, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Color.White) {
            EarningsPeriod.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun WeeklyTotal(period: EarningsPeriod, summary: Earnings?, days: List<DailyEarning>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.card(padding = 18.dp, radius = 18.dp)) {
        Eyebrow(weekLabel(period, summary))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(summary?.totalCents ?: 0, label = "total") {
                Text(it.usd, style = OmwType.LargeTitle.bold, color = OmwColors.Ink)
            }
            summary?.changePercent?.let { change ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(OmwColors.BrandSoft, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Icon(
                        if (change >= 0) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                        null,
                        tint = OmwColors.BrandDeep,
                        modifier = Modifier.size(12.dp),
                    )
                    Text("${if (change >= 0) "+" else ""}$change% vs week before", style = OmwType.Caption.semibold, color = OmwColors.BrandDeep)
                }
            }
        }
        BarChart(days)
    }
}

@Composable
private fun BarChart(days: List<DailyEarning>) {
    val max = days.maxOfOrNull { it.amount } ?: 0.0
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().height(99.dp),
    ) {
        days.forEach { day ->
            val fraction by animateFloatAsState(if (max > 0) (day.amount / max).toFloat() else 0f, label = "bar${day.index}")
            val weekday = DayOfWeek.of(day.index + 1).getDisplayName(TextStyle.FULL, Locale.getDefault())
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxHeight()
                    .semantics(mergeDescendants = true) { contentDescription = "$weekday, ${day.amount.usd}" },
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .width(28.dp)
                            .fillMaxHeight(fraction)
                            .background(
                                if (day.amount > 0 && day.amount == max) OmwColors.Brand else OmwColors.BrandOutline.copy(alpha = 0.55f),
                                RoundedCornerShape(6.dp),
                            ),
                    )
                }
                Text(day.day, style = OmwType.Caption2.semibold, color = OmwColors.InkSecondary)
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.card(padding = 12.dp, radius = 14.dp).semantics(mergeDescendants = true) {},
    ) {
        Text(value, style = OmwType.Title3.bold, color = OmwColors.Ink, maxLines = 1)
        Text(label, style = OmwType.Caption, color = OmwColors.InkSecondary, maxLines = 1)
    }
}

@Composable
private fun CashOut(balanceCents: Int, caption: String, enabled: Boolean, onCashOut: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.card(padding = 14.dp),
    ) {
        IconBadge(R.drawable.wallet_amber, iconSize = 22.dp, badgeSize = 44.dp, background = OmwColors.AmberSoft, radius = 12.dp)
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {},
        ) {
            Text("Available to cash out", style = OmwType.Caption, color = OmwColors.InkSecondary)
            AnimatedContent(balanceCents, label = "balance") {
                Text(it.usd, style = OmwType.Title3.bold, color = OmwColors.Ink)
            }
            Text(caption, style = OmwType.Caption2, color = OmwColors.InkSecondary)
        }
        CompactButton("Cash out", enabled = enabled, onClick = onCashOut)
    }
}

@Composable
private fun Recent(model: AppModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text("Recent deliveries", style = OmwType.Headline, color = OmwColors.Ink)
        }
        if (model.recentDeliveries.isEmpty()) {
            Text("Deliveries you complete show up here.", style = OmwType.Footnote, color = OmwColors.InkSecondary)
        }
        model.recentDeliveries.forEach { delivery ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.card(padding = 12.dp).semantics(mergeDescendants = true) {},
            ) {
                IconBadge(R.drawable.footprints_green, iconSize = 20.dp, badgeSize = 40.dp, background = OmwColors.BrandSoft, radius = 10.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Text(delivery.route, style = OmwType.Subheadline.semibold, color = OmwColors.Ink)
                    Text(delivery.detail, style = OmwType.Caption, color = OmwColors.InkSecondary)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(delivery.amountCents.usd, style = OmwType.Subheadline.bold, color = OmwColors.Ink)
                    Text(delivery.rating, style = OmwType.Caption2, color = OmwColors.InkSecondary)
                }
            }
        }
    }
}
