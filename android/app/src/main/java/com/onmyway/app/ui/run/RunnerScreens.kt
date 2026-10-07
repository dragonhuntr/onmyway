package com.onmyway.app.ui.run

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onmyway.app.R
import com.onmyway.app.model.ApiException
import com.onmyway.app.model.AppModel
import com.onmyway.app.model.ChatTarget
import com.onmyway.app.model.DeliveryRequest
import com.onmyway.app.model.Order
import com.onmyway.app.model.RunRoute
import com.onmyway.app.model.Trip
import com.onmyway.app.model.plain
import com.onmyway.app.model.shortTime
import com.onmyway.app.model.usd
import com.onmyway.app.ui.components.CampusMap
import com.onmyway.app.ui.components.EmptyState
import com.onmyway.app.ui.components.MapItem
import com.onmyway.app.ui.components.MapLabel
import com.onmyway.app.ui.components.MapPin
import com.onmyway.app.ui.components.MapRoute
import com.onmyway.app.ui.components.PinKind
import com.onmyway.app.ui.components.RefreshableColumn
import com.onmyway.app.ui.components.ReportLocationWhileVisible
import com.onmyway.app.ui.components.RunnerDot
import com.onmyway.app.ui.components.TopBar
import com.onmyway.app.ui.components.openUrl
import com.onmyway.app.ui.shared.TripEditor
import com.onmyway.app.ui.theme.ChipToggle
import com.onmyway.app.ui.theme.CompactButton
import com.onmyway.app.ui.theme.CtaButton
import com.onmyway.app.ui.theme.CtaKind
import com.onmyway.app.ui.theme.Eyebrow
import com.onmyway.app.ui.theme.Footer
import com.onmyway.app.ui.theme.Icon
import com.onmyway.app.ui.theme.IconBadge
import com.onmyway.app.ui.theme.OmwColors
import com.onmyway.app.ui.theme.OmwType
import com.onmyway.app.ui.theme.Pill
import com.onmyway.app.ui.theme.bold
import com.onmyway.app.ui.theme.card
import com.onmyway.app.ui.theme.medium
import com.onmyway.app.ui.theme.semibold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class RunFilter(val label: String, val query: String) {
    OnMyRoute("On my route", "on_my_route"),
    Nearby("Nearby", "nearby"),
    ReadyNow("Ready now", "ready_now"),
}

/** Run tab: runner home with request and active delivery pushed on top. */
@Composable
fun RunTab(model: AppModel) {
    val path = model.runPath
    BackHandler(enabled = path.isNotEmpty()) { path.removeAt(path.lastIndex) }

    when (val route = path.lastOrNull()) {
        null -> RunnerHomeScreen(model)
        is RunRoute.Request -> DeliveryRequestScreen(
            model,
            route.request,
            onBack = { path.removeAt(path.lastIndex) },
            onAccepted = { order ->
                path.clear()
                path.add(RunRoute.Active(order))
            },
        )
        // Keyed so a different order starts with fresh screen state.
        is RunRoute.Active -> key(route.order.id) {
            ActiveDeliveryScreen(
                model,
                route.order,
                onBack = { path.removeAt(path.lastIndex) },
                onDone = { path.clear() },
            )
        }
    }
}

/** 07_Runner_Home — earn on walks you're already taking. */
@Composable
private fun RunnerHomeScreen(model: AppModel) {
    var filter by rememberSaveable { mutableStateOf(RunFilter.OnMyRoute) }
    var editingTrip by remember { mutableStateOf<Trip?>(null) }
    var addingTrip by rememberSaveable { mutableStateOf(false) }
    val visible = model.requests

    suspend fun refresh() {
        model.refreshRunner()
        if (model.isAvailable) model.refreshRequests(filter.query)
    }

    // Poll the feed while this screen is showing.
    LaunchedEffect(filter) {
        while (true) {
            refresh()
            delay(5_000)
        }
    }

    RefreshableColumn(
        onRefresh = ::refresh,
        modifier = Modifier.fillMaxSize().background(OmwColors.Canvas).statusBarsPadding(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
        ) {
            Header(model)
            model.activeDelivery?.let { delivery ->
                ResumeBanner(delivery) { model.runPath.add(RunRoute.Active(delivery)) }
            }
            val trip = model.trip
            if (trip != null) {
                NextTrip(trip, onEdit = { editingTrip = trip }, onAdd = { addingTrip = true })
            } else {
                NoTrip { addingTrip = true }
            }
        }
        Filters(filter) { filter = it }
        Orders(model, visible) { model.runPath.add(RunRoute.Request(it)) }
    }

    editingTrip?.let { TripEditor(model, it) { editingTrip = null } }
    if (addingTrip) TripEditor(model, null) { addingTrip = false }
}

@Composable
private fun ResumeBanner(delivery: Order, onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .card(padding = 12.dp),
    ) {
        IconBadge(R.drawable.bag_white_16, iconSize = 16.dp, badgeSize = 32.dp, background = OmwColors.Amber)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
            Text("Delivery in progress", style = OmwType.Subheadline.semibold, color = OmwColors.Ink)
            Text(
                "#${delivery.number} · ${delivery.restaurant.name} → ${delivery.destination.building}",
                style = OmwType.Caption,
                color = OmwColors.InkSecondary,
            )
        }
        Icon(R.drawable.chevron_right, 20.dp)
    }
}

@Composable
private fun Header(model: AppModel) {
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text("Run", style = OmwType.Title.bold, color = OmwColors.Ink, modifier = Modifier.semantics { heading() })
            Text("Earn on walks you’re already taking", style = OmwType.Footnote, color = OmwColors.InkSecondary)
        }
        Text("Available", style = OmwType.Footnote.semibold, color = OmwColors.BrandDeep, modifier = Modifier.padding(end = 8.dp))
        Switch(
            checked = model.isAvailable,
            onCheckedChange = { scope.launch { model.setAvailable(it) } },
            colors = SwitchDefaults.colors(
                checkedTrackColor = OmwColors.Brand,
                uncheckedTrackColor = OmwColors.Hairline,
                uncheckedBorderColor = OmwColors.Hairline,
                uncheckedThumbColor = Color.White,
            ),
            modifier = Modifier.semantics { contentDescription = "Available" },
        )
    }
}

@Composable
private fun TripEyebrow(modifier: Modifier = Modifier) {
    Text(
        "YOUR NEXT TRIP",
        style = OmwType.Caption2.semibold.copy(letterSpacing = 0.88.sp),
        color = OmwColors.OnBrandMuted,
        modifier = modifier,
    )
}

@Composable
private fun NoTrip(onAdd: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(OmwColors.Brand, RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        TripEyebrow()
        Text("Add a walk you’re already taking", style = OmwType.Title3.bold, color = Color.White)
        Text("We’ll show orders that fit your route, sorted by detour.", style = OmwType.Footnote, color = OmwColors.OnBrand)
        Text(
            "+ Add a trip",
            style = OmwType.Footnote.semibold,
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(OmwColors.BrandMid)
                .clickable(role = Role.Button, onClick = onAdd)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun NextTrip(trip: Trip, onEdit: () -> Unit, onAdd: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(OmwColors.Brand, RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TripEyebrow(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(OmwColors.BrandMid, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Icon(R.drawable.clock_white, 14.dp)
                Text("Leaving ${trip.leaveAt.shortTime}", style = OmwType.Caption.semibold, color = Color.White)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            Text(trip.from.name, style = OmwType.Title3.bold, color = Color.White)
            Icon(R.drawable.arrow_right_white, 20.dp, contentDescription = "to")
            Text(trip.to.name, style = OmwType.Title3.bold, color = Color.White)
        }
        Text(
            if (trip.note.isEmpty()) "${trip.walkMinutes} min walk" else "${trip.walkMinutes} min walk · ${trip.note}",
            style = OmwType.Footnote,
            color = OmwColors.OnBrand,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(OmwColors.BrandMid)
                    .clickable(role = Role.Button, onClick = onEdit)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(R.drawable.route_white, 16.dp)
                Text("Edit route", style = OmwType.Footnote.semibold, color = Color.White)
            }
            Text(
                "+ Add another trip",
                style = OmwType.Footnote.semibold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, OmwColors.BrandOutline, RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = onAdd)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Filters(filter: RunFilter, onSelect: (RunFilter) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
    ) {
        RunFilter.entries.forEach { option ->
            ChipToggle(option.label, selected = filter == option) { onSelect(option) }
        }
    }
}

@Composable
private fun Orders(model: AppModel, visible: List<DeliveryRequest>, onOpen: (DeliveryRequest) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Orders along your route", style = OmwType.Headline, color = OmwColors.Ink, modifier = Modifier.weight(1f))
            if (model.isAvailable && visible.isNotEmpty()) {
                Pill("${visible.size} new", foreground = OmwColors.AmberInk, background = OmwColors.AmberSoft)
            }
        }
        when {
            !model.isAvailable -> EmptyState(
                Icons.Outlined.Bedtime,
                "You’re offline",
                "Turn on Available to see orders along your route.",
            )
            visible.isEmpty() -> EmptyState(
                Icons.AutoMirrored.Outlined.DirectionsWalk,
                "No orders right now",
                "We’ll show new orders as they come in.",
            )
            else -> visible.forEach { request -> RequestCard(request) { onOpen(request) } }
        }
    }
}

@Composable
private fun RequestCard(request: DeliveryRequest, onAccept: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.card(padding = 14.dp)) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(painterResource(R.drawable.stop_marker_pickup), null, Modifier.size(12.dp, 34.dp))
                Stop(request.restaurant.name, "Pick up · ${request.readyText}", Modifier.padding(bottom = 8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(R.drawable.stop_marker_dropoff, 12.dp)
                Stop(
                    request.destination.full,
                    if (request.onRoute) "On your way" else "${request.detourMiles.plain()} mi off your route",
                )
            }
        }
        HorizontalDivider(color = OmwColors.Hairline)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("+${request.detourMinutes} min detour", icon = R.drawable.footprints_green)
            Pill(request.payoutCents.usd, foreground = OmwColors.AmberInk, background = OmwColors.AmberSoft)
            Spacer(Modifier.weight(1f))
            CompactButton("Accept", onClick = onAccept)
        }
    }
}

@Composable
private fun Stop(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = modifier) {
        Text(title, style = OmwType.Subheadline.semibold, color = OmwColors.Ink)
        Text(detail, style = OmwType.Caption, color = OmwColors.InkSecondary)
    }
}

/** 08_Delivery_Request — pickup/drop-off detail before accepting. */
@Composable
private fun DeliveryRequestScreen(
    model: AppModel,
    request: DeliveryRequest,
    onBack: () -> Unit,
    onAccepted: (Order) -> Unit,
) {
    var isAccepting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val customerFirstName = request.customer.name.substringBefore(" ")

    Column(Modifier.fillMaxSize().background(OmwColors.Canvas)) {
        TopBar("Delivery request", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            CampusMap(height = 190.dp, route = MapRoute.Request) {
                MapItem(49f, 140.6f) { MapPin(PinKind.Pickup, "Pick up") }
                MapItem(330f, 30.4f) { MapPin(PinKind.Dropoff, "Drop off") }
                MapItem(150f, 49f) { MapLabel("Your route", filled = true) }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Image(painterResource(R.drawable.footprints_dark), null, Modifier.size(14.17.dp, 11.33.dp))
                    Text(
                        "+${request.detourMinutes} min detour · ${request.detourMiles.plain()} mi off your route",
                        style = OmwType.Caption.semibold,
                        color = OmwColors.Ink,
                    )
                }

                Column(Modifier.card()) {
                    StopRow(
                        PinKind.Pickup,
                        "Pick up · ${request.restaurant.name}",
                        "${request.readyText} · Give order #${request.number} at pickup",
                    )
                    StopRow(
                        PinKind.Dropoff,
                        "Drop off · ${request.destination.full}",
                        "By ${request.dropoffBy.shortTime} · Meet $customerFirstName",
                        isLast = true,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.card()) {
                    Eyebrow("PICK UP ON TRANSACT")
                    LabeledValue("Order number", "#${request.number}")
                    LabeledValue("Name on order", request.nameOnOrder)
                    if (request.destination.note.isNotEmpty()) {
                        Text(
                            "“${request.destination.note}”",
                            style = OmwType.Footnote,
                            color = OmwColors.InkSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(OmwColors.Canvas, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.card(padding = 12.dp),
                ) {
                    Avatar("Photo of ${request.customer.name}")
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        Text(request.customer.name, style = OmwType.Subheadline.semibold, color = OmwColors.Ink)
                        Text(
                            if (request.customer.orders == 1) "1 order" else "${request.customer.orders} orders",
                            style = OmwType.Caption,
                            color = OmwColors.InkSecondary,
                        )
                    }
                    Pill("Penn State", icon = R.drawable.shield_green)
                }
            }
        }
        Footer {
            errorMessage?.let { Text(it, style = OmwType.Footnote.semibold, color = OmwColors.InkSecondary) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CtaButton("Skip", Modifier.width(104.dp), kind = CtaKind.Outline, onClick = onBack)
                CtaButton(
                    if (isAccepting) "Accepting…" else "Accept delivery · ${request.payoutCents.usd}",
                    Modifier.weight(1f),
                    enabled = !isAccepting,
                ) {
                    scope.launch {
                        isAccepting = true
                        try {
                            onAccepted(model.accept(request))
                        } catch (e: ApiException) {
                            errorMessage = e.message
                        } finally {
                            isAccepting = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Text(
        buildAnnotatedString {
            append("$label  ")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(value) }
        },
        style = OmwType.Subheadline.medium,
        color = OmwColors.Ink,
    )
}

@Composable
private fun Avatar(description: String) {
    Image(
        painterResource(R.drawable.customer_avatar),
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(44.dp).clip(CircleShape),
    )
}

@Composable
private fun StopRow(kind: PinKind, title: String, detail: String, isLast: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(
                if (kind == PinKind.Pickup) R.drawable.bag_white_16 else R.drawable.pin_white_16,
                iconSize = 16.dp,
                badgeSize = 32.dp,
                background = if (kind == PinKind.Pickup) OmwColors.Amber else OmwColors.Brand,
            )
            if (!isLast) Box(Modifier.size(2.dp, 28.dp).background(OmwColors.Hairline, RoundedCornerShape(1.dp)))
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(top = 4.dp, bottom = if (isLast) 0.dp else 14.dp),
        ) {
            Text(title, style = OmwType.Subheadline.semibold, color = OmwColors.Ink)
            Text(detail, style = OmwType.Footnote, color = OmwColors.InkSecondary)
        }
    }
}

private val reportReasons = listOf("Order isn’t ready", "Wrong order number", "Can’t find the customer", "Other")

/**
 * 09_Active_Delivery — step-by-step pickup and hand-off.
 *
 * Rebuilt from the Figma layer structure (the design-context export for this frame was not
 * available), so colors and icons follow the other screens' tokens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveDeliveryScreen(model: AppModel, initial: Order, onBack: () -> Unit, onDone: () -> Unit) {
    var order by remember { mutableStateOf(initial) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val atPickup = order.status == Order.Status.Accepted
    val customerName = order.customer?.name ?: order.nameOnOrder
    val runnerX by animateFloatAsState(if (atPickup) 102f else 240f, label = "runnerX")
    val runnerY by animateFloatAsState(if (atPickup) 108f else 66f, label = "runnerY")

    ReportLocationWhileVisible { latitude, longitude ->
        scope.launch { model.reportLocation(latitude, longitude) }
    }

    fun advance() = scope.launch {
        isSaving = true
        errorMessage = null
        try {
            if (atPickup) {
                order = model.confirmPickup(order)
            } else {
                model.confirmDropoff(order)
                onDone()
            }
        } catch (e: ApiException) {
            errorMessage = e.message
        } finally {
            isSaving = false
        }
    }

    /** Walking directions in Google Maps to the next stop's building. */
    fun openDirections() {
        val building = if (atPickup) order.restaurant.building.name else order.destination.building
        val destination = Uri.encode("$building, Penn State Behrend, Erie, PA")
        context.openUrl("https://www.google.com/maps/dir/?api=1&destination=$destination&travelmode=walking")
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        TopBar(if (atPickup) "Head to pickup" else "Deliver order", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OmwColors.Canvas)
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                    .semantics { contentDescription = "Step ${if (atPickup) 1 else 2} of 3" },
            ) {
                repeat(3) { step ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .background(if (step < (if (atPickup) 1 else 2)) OmwColors.Brand else OmwColors.Hairline, CircleShape),
                    )
                }
            }

            // The sheet overlaps the map by its corner radius.
            Box {
                CampusMap(height = 224.dp, route = MapRoute.Request) {
                    MapItem(34f, 148f) { MapPin(PinKind.Pickup, order.restaurant.name) }
                    MapItem(321.5f, 32f) { MapPin(PinKind.Dropoff, order.destination.building) }
                    MapItem(runnerX, runnerY) { RunnerDot(contentDescription = "You") }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .padding(top = 200.dp)
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                            Text(
                                if (atPickup) order.restaurant.name else order.destination.full,
                                style = OmwType.Title3.bold,
                                color = OmwColors.Ink,
                            )
                            Text(
                                if (atPickup) {
                                    "${order.restaurant.building.name} · Ready at ${order.readyAt?.shortTime ?: "any minute"}"
                                } else {
                                    "${order.walkMinutes} min walk from pickup"
                                },
                                style = OmwType.Footnote,
                                color = OmwColors.InkSecondary,
                            )
                        }
                        IconBadge(
                            R.drawable.navigation, iconSize = 22.dp, badgeSize = 44.dp, background = Color.White,
                            border = BorderStroke(1.dp, OmwColors.Hairline),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(role = Role.Button, onClick = ::openDirections)
                                .semantics { contentDescription = "Directions" },
                        )
                    }

                    if (atPickup) OrderNumberCard(order)

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar("Photo of $customerName")
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
                            Text(
                                if (order.destination.room.isEmpty()) customerName else "$customerName · ${order.destination.room}",
                                style = OmwType.Subheadline.semibold,
                                color = OmwColors.Ink,
                            )
                            if (order.destination.note.isNotEmpty()) {
                                Text("“${order.destination.note}”", style = OmwType.Caption, color = OmwColors.InkSecondary)
                            }
                        }
                        CircleButton(Icons.Outlined.ChatBubbleOutline, "Message $customerName") {
                            model.chat = ChatTarget(order.id, customerName)
                        }
                    }
                }
            }
        }
        Footer {
            errorMessage?.let { Text(it, style = OmwType.Footnote.semibold, color = OmwColors.InkSecondary) }
            CtaButton(if (atPickup) "Confirm pickup" else "Confirm drop-off", enabled = !isSaving) { advance() }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.dp)
                    .clickable(role = Role.Button) { reporting = true },
            ) {
                MaterialIcon(
                    Icons.Outlined.Close,
                    contentDescription = null,
                    tint = OmwColors.InkSecondary,
                    modifier = Modifier.size(16.dp),
                )
                Text("Something’s wrong with this order", style = OmwType.Footnote.medium, color = OmwColors.InkSecondary)
            }
        }
    }

    if (reporting) {
        ModalBottomSheet(onDismissRequest = { reporting = false }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                Text(
                    "What’s wrong?",
                    style = OmwType.Headline,
                    color = OmwColors.Ink,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                reportReasons.forEach { reason ->
                    SheetAction(reason, OmwColors.Ink) {
                        reporting = false
                        scope.launch { runCatching { model.report(order.id, reason, "") } }
                    }
                }
                if (atPickup) {
                    SheetAction("Hand back this order", Color(0xFFC62828)) {
                        reporting = false
                        scope.launch {
                            try {
                                model.release(order)
                                onDone()
                            } catch (e: ApiException) {
                                errorMessage = e.message
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetAction(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        style = OmwType.Body,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

@Composable
private fun OrderNumberCard(order: Order) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(OmwColors.BrandSoft, RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            MaterialIcon(
                Icons.AutoMirrored.Outlined.ReceiptLong,
                contentDescription = null,
                tint = OmwColors.BrandDeep,
                modifier = Modifier.size(14.dp),
            )
            Text(
                "GIVE THIS ORDER NUMBER AT PICKUP",
                style = OmwType.Caption.semibold.copy(letterSpacing = 0.72.sp),
                color = OmwColors.BrandDeep,
            )
        }
        Text("#${order.number}", style = OmwType.LargeTitle.bold, color = OmwColors.Ink)
        Text("Name on order · ${order.nameOnOrder}", style = OmwType.Footnote, color = OmwColors.InkSecondary)
    }
}

@Composable
private fun CircleButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(OmwColors.BrandSoft)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        MaterialIcon(icon, contentDescription = null, tint = OmwColors.Brand, modifier = Modifier.size(20.dp))
    }
}
