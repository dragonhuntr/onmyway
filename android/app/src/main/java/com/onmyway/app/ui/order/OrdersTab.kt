package com.onmyway.app.ui.order

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onmyway.app.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.rememberCoroutineScope
import com.onmyway.app.model.ApiException
import com.onmyway.app.model.ChatTarget
import com.onmyway.app.model.Runner
import com.onmyway.app.model.shortTime
import com.onmyway.app.model.usd
import com.onmyway.app.ui.components.rememberPayments
import com.stripe.android.paymentsheet.PaymentSheetResult
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import com.onmyway.app.model.AppModel
import com.onmyway.app.model.AppTab
import com.onmyway.app.model.Order
import com.onmyway.app.ui.components.CampusMap
import com.onmyway.app.ui.components.EmptyState
import com.onmyway.app.ui.components.LargeTitle
import com.onmyway.app.ui.components.MapItem
import com.onmyway.app.ui.components.MapLabel
import com.onmyway.app.ui.components.MapPin
import com.onmyway.app.ui.components.MapRoute
import com.onmyway.app.ui.components.PinKind
import com.onmyway.app.ui.components.RunnerDot
import com.onmyway.app.ui.theme.ChipToggle
import com.onmyway.app.ui.theme.CtaButton
import com.onmyway.app.ui.theme.CtaKind
import com.onmyway.app.ui.theme.Footer
import com.onmyway.app.ui.theme.Icon
import com.onmyway.app.ui.theme.IconBadge
import com.onmyway.app.ui.theme.OmwColors
import com.onmyway.app.ui.theme.OmwType
import com.onmyway.app.ui.theme.bold
import com.onmyway.app.ui.theme.medium
import com.onmyway.app.ui.theme.semibold
import com.onmyway.app.ui.theme.weight

@Composable
fun OrdersTab(model: AppModel) {
    val order = model.activeOrder
    val runner = order?.runner
    if (order != null && runner != null) {
        OrderTrackingScreen(model, order, runner)
    } else if (order != null && order.status == Order.Status.Matching && model.orderingFrom == null) {
        FindingRunnerScreen(model, onBack = null)
    } else {
        Column(Modifier.fillMaxSize().background(OmwColors.Canvas).statusBarsPadding()) {
            LargeTitle("Orders", Modifier.padding(start = 20.dp, top = 8.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = "No active orders",
                    message = "Order on Transact, then send it to a runner heading your way.",
                ) {
                    Button(
                        onClick = { model.selectedTab = AppTab.Home },
                        colors = ButtonDefaults.buttonColors(containerColor = OmwColors.Brand),
                    ) { Text("Browse dining halls") }
                }
            }
        }
    }
}

/** 05_Order_Tracking — live map plus runner details and delivery timeline. */
@Composable
private fun OrderTrackingScreen(model: AppModel, order: Order, runner: Runner) {
    val eta = order.etaMinutes ?: order.walkMinutes
    val pickedUp = order.status == Order.Status.PickedUp
    // Where the runner dot sits on the illustrated map: near pickup until picked up, then halfway.
    val runnerX by animateFloatAsState(if (pickedUp) 204.9f else 70f, label = "runnerX")
    val runnerY by animateFloatAsState(if (pickedUp) 111.6f else 200f, label = "runnerY")

    Column(
        Modifier
            .fillMaxSize()
            .background(OmwColors.Canvas)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding(),
    ) {
        CampusMap(height = 310.dp, route = MapRoute.Tracking) {
            MapItem(34f, 229.4f) { MapPin(PinKind.Pickup, order.restaurant.name) }
            MapItem(321.5f, 49.6f) { MapPin(PinKind.Dropoff, order.destination.building) }
            MapItem(runnerX, runnerY) { RunnerDot(contentDescription = "${runner.name}, $eta minutes away") }
            MapItem(runnerX, runnerY - 35.6f) { MapLabel("${runner.firstName} · $eta min", filled = true) }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .shadow(8.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), ambientColor = Color(0x1F14211A), spotColor = Color(0x1F14211A))
                .background(Color.White, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp, 5.dp).background(OmwColors.Hairline, CircleShape))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.runner_avatar),
                    contentDescription = "Photo of ${runner.name}, your runner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        if (pickedUp) "${runner.firstName} is on the way" else "${runner.firstName} is getting your food",
                        style = OmwType.Title3.bold,
                        color = OmwColors.Ink,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (runner.rating != null) Icon(R.drawable.star, 14.dp)
                        Text(runner.summary, style = OmwType.Footnote, color = OmwColors.InkSecondary)
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(OmwColors.BrandSoft, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .semantics(mergeDescendants = true) {},
                ) {
                    Text("$eta min", style = OmwType.Callout.bold, color = OmwColors.BrandDeep)
                    Text("ETA", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = OmwColors.BrandDeep)
                }
            }

            runner.routeNote?.let { routeNote ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OmwColors.AmberSoft, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Icon(R.drawable.footprints_amber, 18.dp)
                    Text(routeNote, style = OmwType.Footnote, color = OmwColors.AmberInk)
                }
            }

            Column {
                val arriving = Instant.now().plus(Duration.ofMinutes(eta.toLong())).shortTime
                TimelineStep("Order #${order.number} sent to ${runner.firstName}", order.acceptedAt?.shortTime, StepState.Done)
                TimelineStep(
                    if (pickedUp) "Picked up at ${order.restaurant.name}" else "Picking up at ${order.restaurant.name}",
                    order.pickedUpAt?.shortTime ?: order.readyAt?.let { "Ready ~${it.shortTime}" },
                    if (pickedUp) StepState.Done else StepState.Current,
                )
                TimelineStep(
                    "On the way · ${order.destination.building}",
                    if (pickedUp) "Arriving ~$arriving" else null,
                    if (pickedUp) StepState.Current else StepState.Upcoming,
                )
                TimelineStep("Delivered", null, StepState.Upcoming, isLast = true)
            }

            CtaButton("Message ${runner.firstName}", kind = CtaKind.Secondary) {
                model.chat = ChatTarget(order.id, runner.name)
            }
        }
    }
}

enum class StepState { Done, Current, Upcoming }

@Composable
fun TimelineStep(title: String, subtitle: String?, state: StepState, modifier: Modifier = Modifier, isLast: Boolean = false) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                stateDescription = when (state) {
                    StepState.Done -> "Complete"
                    StepState.Current -> "In progress"
                    StepState.Upcoming -> "Not started"
                }
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (state) {
                StepState.Done -> IconBadge(R.drawable.check_small_white, iconSize = 12.dp, badgeSize = 20.dp, background = OmwColors.Brand)
                StepState.Current -> Icon(R.drawable.timeline_current, 20.dp)
                StepState.Upcoming -> Box(
                    Modifier
                        .size(20.dp)
                        .background(Color.White, CircleShape)
                        .border(2.5.dp, OmwColors.Hairline, CircleShape),
                )
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(22.dp)
                        .background(if (state == StepState.Done) OmwColors.Brand else OmwColors.Hairline, RoundedCornerShape(1.dp)),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                title,
                style = OmwType.Subheadline.weight(if (state == StepState.Current) FontWeight.SemiBold else FontWeight.Medium),
                color = if (state == StepState.Upcoming) OmwColors.InkSecondary else OmwColors.Ink,
            )
            if (subtitle != null) Text(subtitle, style = OmwType.Caption, color = OmwColors.InkSecondary)
        }
    }
}

/** 06_Delivered — confirmation, savings, rating, and optional extra tip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeliveredScreen(model: AppModel, order: Order) {
    var rating by rememberSaveable { mutableIntStateOf(5) }
    var tags by remember { mutableStateOf(emptySet<String>()) }
    var extraTip by rememberSaveable { mutableStateOf<Int?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val payments = rememberPayments()
    val scope = rememberCoroutineScope()
    val feedback = listOf("On time", "Friendly", "Careful with food")
    val extraTips = listOf(100, 200, 300)
    val runnerName = order.runner?.firstName ?: "your runner"
    val cardShape = RoundedCornerShape(16.dp)

    /** Sends the rating (and pays any extra tip), then closes. */
    suspend fun finish(orderAgain: Boolean) {
        isSubmitting = true
        errorMessage = null
        try {
            if (order.rating == null) {
                val tip = model.rate(order, rating, tags.toList(), extraTip ?: 0)
                if (tip != null) {
                    val (tipId, payment) = tip
                    val result = payments.pay(payment)
                    if (result is PaymentSheetResult.Failed) {
                        errorMessage = result.error.localizedMessage ?: "Payment failed. Try again."
                        return
                    }
                    runCatching { model.confirmTip(tipId) }
                }
            }
        } catch (e: ApiException) {
            // "already_rated": rated on an earlier tap; just close.
            if (e.code != "already_rated") {
                errorMessage = e.message
                return
            }
        } finally {
            isSubmitting = false
        }
        model.finishDelivered(orderAgain)
    }

    Column(Modifier.fillMaxSize().background(OmwColors.Canvas)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.background(OmwColors.BrandSoft, CircleShape).padding(12.dp)) {
                    IconBadge(R.drawable.check_large_white, iconSize = 40.dp, badgeSize = 64.dp, background = OmwColors.Brand)
                }
                Text("Delivered!", style = OmwType.Title.bold, color = OmwColors.Ink)
                Text(
                    "$runnerName handed off your order at ${order.destination.full}${order.deliveredAt?.let { " at ${it.shortTime}" }.orEmpty()}.",
                    style = OmwType.Subheadline,
                    color = OmwColors.InkSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OmwColors.AmberSoft, RoundedCornerShape(14.dp))
                    .padding(14.dp)
                    .semantics(mergeDescendants = true) {},
            ) {
                IconBadge(R.drawable.dollar, iconSize = 20.dp, badgeSize = 40.dp, background = OmwColors.Amber)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("You paid ${order.deliveryFeeCents.usd} for delivery", style = OmwType.Subheadline.semibold, color = OmwColors.AmberInkDeep)
                    Text("$5.99 less than the cheapest commercial app.", style = OmwType.Footnote, color = OmwColors.AmberInk)
                }
            }

            // Rate card
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, cardShape)
                    .border(1.dp, OmwColors.Hairline, cardShape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("How was $runnerName?", style = OmwType.Headline, color = OmwColors.Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { star ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.RadioButton) { rating = star }
                                .semantics {
                                    contentDescription = "$star star${if (star == 1) "" else "s"}"
                                    selected = star == rating
                                },
                        ) {
                            Icon(if (star <= rating) R.drawable.star_filled else R.drawable.star_empty, 34.dp)
                        }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    feedback.forEach { tag ->
                        ChipToggle(tag, selected = tag in tags) {
                            tags = if (tag in tags) tags - tag else tags + tag
                        }
                    }
                }
            }

            // Tip card
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, cardShape)
                    .border(1.dp, OmwColors.Hairline, cardShape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add to your ${order.tipCents.usd} tip?", style = OmwType.Subheadline.semibold, color = OmwColors.Ink)
                    Spacer(Modifier.weight(1f))
                    Text("100% to $runnerName", style = OmwType.Caption.medium, color = OmwColors.InkSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    extraTips.forEach { tip ->
                        ChipToggle("+${tip.usd}", selected = extraTip == tip, fillsWidth = true, modifier = Modifier.weight(1f)) {
                            extraTip = if (extraTip == tip) null else tip
                        }
                    }
                }
            }
        }
        Footer {
            errorMessage?.let { Text(it, style = OmwType.Footnote.semibold, color = OmwColors.InkSecondary) }
            CtaButton("Done", enabled = !isSubmitting) { scope.launch { finish(orderAgain = false) } }
            CtaButton("Order again", kind = CtaKind.Secondary, enabled = !isSubmitting) { scope.launch { finish(orderAgain = true) } }
        }
    }
}
