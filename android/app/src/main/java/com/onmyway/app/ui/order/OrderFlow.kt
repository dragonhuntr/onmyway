package com.onmyway.app.ui.order

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.onmyway.app.R
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.onmyway.app.model.ApiException
import com.onmyway.app.model.shortTime
import com.onmyway.app.model.usd
import com.onmyway.app.ui.components.rememberPayments
import com.onmyway.app.ui.shared.DestinationPicker
import com.onmyway.app.ui.shared.showTimePicker
import com.stripe.android.paymentsheet.PaymentSheetResult
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import com.onmyway.app.model.AppModel
import com.onmyway.app.model.Restaurant
import com.onmyway.app.model.Tip
import com.onmyway.app.ui.components.CampusMap
import com.onmyway.app.ui.components.MapItem
import com.onmyway.app.ui.components.MapPin
import com.onmyway.app.ui.components.MapRoute
import com.onmyway.app.ui.components.PinKind
import com.onmyway.app.ui.components.RunnerDot
import com.onmyway.app.ui.components.StatusCapsule
import com.onmyway.app.ui.components.TopBar
import com.onmyway.app.ui.theme.ChipToggle
import com.onmyway.app.ui.theme.CtaButton
import com.onmyway.app.ui.theme.CtaKind
import com.onmyway.app.ui.theme.Eyebrow
import com.onmyway.app.ui.theme.Footer
import com.onmyway.app.ui.theme.Icon
import com.onmyway.app.ui.theme.IconBadge
import com.onmyway.app.ui.theme.LinkButton
import com.onmyway.app.ui.theme.OmwColors
import com.onmyway.app.ui.theme.OmwType
import com.onmyway.app.ui.theme.bold
import com.onmyway.app.ui.theme.card
import com.onmyway.app.ui.theme.medium
import com.onmyway.app.ui.theme.semibold

private enum class OrderStep { EnterNumber, FindingRunner }

/** Full-screen ordering flow: Transact web ordering → order number → runner matching. */
@Composable
fun OrderFlow(model: AppModel, restaurant: Restaurant) {
    val path = remember { mutableStateListOf<OrderStep>() }

    // The web view stays composed underneath the later steps so the Transact receipt survives "Reopen receipt".
    Box(Modifier.fillMaxSize()) {
        TransactScreen(model, restaurant, onNext = { path.add(OrderStep.EnterNumber) }, isCovered = path.isNotEmpty())
        when (path.lastOrNull()) {
            OrderStep.EnterNumber -> EnterOrderNumberScreen(
                model,
                restaurant,
                onBack = { path.removeAt(path.lastIndex) },
                onReopenReceipt = { path.clear() },
                onSent = { path.add(OrderStep.FindingRunner) },
            )
            OrderStep.FindingRunner -> FindingRunnerScreen(model, onBack = { path.removeAt(path.lastIndex) })
            null -> Unit
        }
    }

    BackHandler(enabled = path.isNotEmpty()) { path.removeAt(path.lastIndex) }
}

/** 02_Order_Webview — food is ordered and paid on Transact's own site. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TransactScreen(model: AppModel, restaurant: Restaurant, onNext: () -> Unit, isCovered: Boolean) {
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    isLoading = true
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    isLoading = false
                    canGoBack = view.canGoBack()
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) isLoading = false
                }
            }
            loadUrl(restaurant.orderingUrl)
        }
    }
    DisposableEffect(webView) { onDispose { webView.destroy() } }

    BackHandler(enabled = !isCovered) {
        if (canGoBack) webView.goBack() else model.orderingFrom = null
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        TopBar(
            title = restaurant.name,
            onBack = { model.orderingFrom = null },
            background = Color.White,
            navigationIcon = Icons.Filled.Close,
            navigationLabel = "Close",
            titleContent = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                ) {
                    Text(restaurant.name, style = OmwType.Subheadline.semibold, color = OmwColors.Ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(R.drawable.shield_small, 12.dp)
                        Text(Uri.parse(restaurant.orderingUrl).host.orEmpty(), style = OmwType.Caption, color = OmwColors.InkSecondary)
                    }
                }
            },
        )
        HorizontalDivider(color = OmwColors.Hairline)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (isLoading) CircularProgressIndicator(color = OmwColors.InkSecondary)
        }
        Footer {
            Row(Modifier.padding(horizontal = 4.dp)) {
                Text("Checked out on Transact?", style = OmwType.Footnote.medium, color = OmwColors.InkSecondary)
                Spacer(Modifier.weight(1f))
                Text("Next: send it to a runner", style = OmwType.Footnote.medium, color = OmwColors.BrandDeep)
            }
            CtaButton("Enter order number", onClick = onNext)
        }
    }
}

/** 03_Enter_Order_Number — hand the paid Transact order to a runner. */
@Composable
private fun EnterOrderNumberScreen(
    model: AppModel,
    restaurant: Restaurant,
    onBack: () -> Unit,
    onReopenReceipt: () -> Unit,
    onSent: () -> Unit,
) {
    var orderNumber by rememberSaveable { mutableStateOf("") }
    var nameOnOrder by rememberSaveable { mutableStateOf(model.session?.shortName.orEmpty()) }
    var readyAt by remember { mutableStateOf(Instant.now().plus(Duration.ofMinutes(10))) }
    var tip by rememberSaveable { mutableStateOf(Tip.One) }
    var choosingDestination by rememberSaveable { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val payments = rememberPayments()
    val scope = rememberCoroutineScope()
    val deliveryFee = 100
    val due = deliveryFee + tip.cents

    /** Creates the order, takes the delivery fee + tip through Stripe, then starts matching. */
    suspend fun submit() {
        if (model.destination == null) {
            choosingDestination = true
            return
        }
        isSubmitting = true
        errorMessage = null
        try {
            val payment = model.createOrder(restaurant, orderNumber.trim(), nameOnOrder, readyAt, tip)
            if (payment != null) {
                when (val result = payments.pay(payment)) {
                    is PaymentSheetResult.Completed -> model.confirmPayment()
                    is PaymentSheetResult.Canceled -> return
                    is PaymentSheetResult.Failed -> {
                        errorMessage = result.error.localizedMessage ?: "Payment failed. Try again."
                        return
                    }
                }
            }
            onSent()
        } catch (e: ApiException) {
            errorMessage = e.message
        } finally {
            isSubmitting = false
        }
    }

    Column(Modifier.fillMaxSize().background(OmwColors.Canvas).imePadding()) {
        TopBar("Send to a runner", onBack = onBack)
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
        ) {
            PaidBanner()
            OrderNumberCard(
                restaurant,
                orderNumber, { orderNumber = it.filter(Char::isDigit) },
                nameOnOrder, { nameOnOrder = it },
                readyAt, { readyAt = it },
                onReopenReceipt,
            )
            DeliverToCard(model) { choosingDestination = true }
            TipCard(tip) { tip = it }
            SummaryCard(deliveryFee, tip, due)
            PaymentCard()
        }
        Footer {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Icon(R.drawable.shield, 16.dp)
                Text("Runner ID verified · Every order is protected", style = OmwType.Caption.medium, color = OmwColors.InkSecondary)
            }
            errorMessage?.let {
                Text(it, style = OmwType.Footnote.semibold, color = OmwColors.InkSecondary, modifier = Modifier.fillMaxWidth())
            }
            CtaButton(
                if (isSubmitting) "Sending…" else "Send to runner · ${due.usd}",
                enabled = orderNumber.isNotBlank() && !isSubmitting,
            ) { scope.launch { submit() } }
        }
    }

    if (choosingDestination) DestinationPicker(model) { choosingDestination = false }
}

@Composable
private fun PaidBanner() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(OmwColors.BrandSoft, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        IconBadge(R.drawable.check_white, iconSize = 16.dp, badgeSize = 32.dp, background = OmwColors.Brand)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Food paid on Transact", style = OmwType.Subheadline.semibold, color = OmwColors.Ink)
            Text(
                "Add your order number and we’ll pass it to a runner heading your way.",
                style = OmwType.Footnote,
                color = OmwColors.InkSecondary,
            )
        }
    }
}

@Composable
private fun OrderNumberCard(
    restaurant: Restaurant,
    orderNumber: String,
    onNumberChange: (String) -> Unit,
    nameOnOrder: String,
    onNameChange: (String) -> Unit,
    readyAt: Instant,
    onReadyAtChange: (Instant) -> Unit,
    onReopenReceipt: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.card()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("TRANSACT ORDER · ${restaurant.name}", Modifier.weight(1f))
            LinkButton("Reopen receipt", onClick = onReopenReceipt)
        }
        BasicTextField(
            value = orderNumber,
            onValueChange = onNumberChange,
            singleLine = true,
            textStyle = OmwType.Title2.bold.copy(color = OmwColors.Ink),
            cursorBrush = SolidColor(OmwColors.Brand),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = "Order number" },
            decorationBox = { inner ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(Color.White, shape)
                        .border(2.dp, if (focused) OmwColors.Brand else OmwColors.Hairline, shape)
                        .padding(horizontal = 16.dp),
                ) {
                    Text("#", style = OmwType.Title2.semibold, color = OmwColors.InkSecondary)
                    Box(Modifier.weight(1f)) {
                        if (orderNumber.isEmpty()) Text("Order number", style = OmwType.Title2.bold, color = OmwColors.Hairline)
                        inner()
                    }
                }
            },
        )
        Text(
            "It’s at the top of your Transact confirmation screen and email.",
            style = OmwType.Footnote,
            color = OmwColors.InkSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoField("Name on order", Modifier.weight(1f)) {
                BasicTextField(
                    value = nameOnOrder,
                    onValueChange = onNameChange,
                    singleLine = true,
                    textStyle = OmwType.Subheadline.semibold.copy(color = OmwColors.Ink),
                    cursorBrush = SolidColor(OmwColors.Brand),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Name on order" },
                    decorationBox = { inner ->
                        Box {
                            if (nameOnOrder.isEmpty()) Text("Name", style = OmwType.Subheadline.semibold, color = OmwColors.InkSecondary)
                            inner()
                        }
                    },
                )
            }
            InfoField(
                "Ready around",
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = "Change time") {
                        showTimePicker(context, readyAt, onReadyAtChange)
                    },
            ) {
                Text(readyAt.shortTime, style = OmwType.Subheadline.semibold, color = OmwColors.Brand)
            }
        }
    }
}

@Composable
private fun InfoField(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .background(OmwColors.Canvas, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label, style = OmwType.Caption.medium, color = OmwColors.InkSecondary)
        value()
    }
}

@Composable
private fun DeliverToCard(model: AppModel, onChange: () -> Unit) {
    val destination = model.destination
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.card()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("DELIVER TO", Modifier.weight(1f))
            LinkButton(if (destination == null) "Choose" else "Change", onClick = onChange)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
            IconBadge(R.drawable.building, iconSize = 22.dp, badgeSize = 44.dp, background = OmwColors.BrandSoft, radius = 12.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(destination?.full ?: "Pick a building", style = OmwType.Callout.semibold, color = OmwColors.Ink)
                destination?.note?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = OmwType.Footnote, color = OmwColors.InkSecondary)
                }
            }
        }
    }
}

@Composable
private fun TipCard(tip: Tip, onSelect: (Tip) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.card()) {
        Eyebrow("TIP YOUR RUNNER")
        Text("100% goes to the student who walks it over.", style = OmwType.Footnote, color = OmwColors.InkSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tip.entries.forEach { option ->
                ChipToggle(option.label, selected = tip == option, fillsWidth = true, modifier = Modifier.weight(1f)) {
                    onSelect(option)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(deliveryFeeCents: Int, tip: Tip, dueCents: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.card()) {
        SummaryRow("Food · paid on Transact", "Paid", OmwColors.Brand)
        SummaryRow("Delivery fee", deliveryFeeCents.usd)
        Text(
            "You save $5.99 vs. a $6.99 fee on commercial delivery apps",
            style = OmwType.Caption.medium,
            color = OmwColors.AmberInk,
            modifier = Modifier
                .fillMaxWidth()
                .background(OmwColors.AmberSoft, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        SummaryRow("Runner tip", tip.cents.usd)
        HorizontalDivider(color = OmwColors.Hairline)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Due now", style = OmwType.Subheadline.semibold, color = OmwColors.Ink, modifier = Modifier.weight(1f))
            Text(dueCents.usd, style = OmwType.Title3.bold, color = OmwColors.Ink)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color = OmwColors.Ink) {
    Row {
        Text(label, style = OmwType.Subheadline, color = OmwColors.InkSecondary, modifier = Modifier.weight(1f))
        Text(value, style = OmwType.Subheadline.medium, color = valueColor)
    }
}

@Composable
private fun PaymentCard() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.card(padding = 14.dp).semantics(mergeDescendants = true) {},
    ) {
        IconBadge(R.drawable.wallet_amber, iconSize = 20.dp, badgeSize = 40.dp, background = OmwColors.AmberSoft, radius = 10.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text("Card · chosen at checkout", style = OmwType.Subheadline.medium, color = OmwColors.Ink)
            Text("Held now, charged when it’s delivered. Delivery + tip only.", style = OmwType.Footnote, color = OmwColors.InkSecondary)
        }
    }
}

/** 04_Finding_Runner — match with a student already walking this way. */
@Composable
fun FindingRunnerScreen(model: AppModel, onBack: (() -> Unit)?) {
    val progress = remember { Animatable(0.1f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        // The model polls the order and closes this flow once a runner accepts.
        // The bar only shows that matching is under way; most matches take under 5 minutes.
        progress.animateTo(0.95f, tween(300_000, easing = LinearOutSlowInEasing))
    }
    val building = model.activeOrder?.destination?.building ?: model.destination?.building ?: "your building"
    val runners = model.runnersHeadingYourWay

    Column(Modifier.fillMaxSize().background(OmwColors.Canvas)) {
        TopBar("Finding your runner", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            CampusMap(height = 360.dp, route = MapRoute.Finding) {
                MapItem(34f, 266.4f) { MapPin(PinKind.Pickup, model.activeOrder?.restaurant?.name.orEmpty()) }
                MapItem(321.5f, 57.6f) { MapPin(PinKind.Dropoff, building) }
                MapItem(150f, 150f) { RunnerDot() }
                MapItem(300f, 250f) { RunnerDot() }
                MapItem(200f, 300f) { RunnerDot() }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
                StatusCapsule("Matching…") { Image(painterResource(R.drawable.loading_dots), null, Modifier.size(26.dp, 6.dp)) }
                Text("Looking for a runner on their way to $building", style = OmwType.Title3.bold, color = OmwColors.Ink)
                LinearProgressIndicator(
                    progress = { progress.value },
                    color = OmwColors.Brand,
                    trackColor = OmwColors.Hairline,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .semantics { contentDescription = "Matching progress" },
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    InfoPill(
                        R.drawable.footprints,
                        if (runners == 1) "1 runner heading your way" else "$runners runners heading your way",
                    )
                    InfoPill(R.drawable.clock, "Usually under 5 min")
                }
                model.activeOrder?.let { order ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.card(padding = 14.dp, radius = 14.dp).semantics(mergeDescendants = true) {},
                    ) {
                        IconBadge(R.drawable.receipt_amber, iconSize = 20.dp, badgeSize = 40.dp, background = OmwColors.AmberSoft, radius = 10.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Order #${order.number} · ${order.restaurant.name}", style = OmwType.Subheadline.semibold, color = OmwColors.Ink)
                            Text(
                                "${order.nameOnOrder} · Ready at ~${order.readyAt?.shortTime ?: "soon"}",
                                style = OmwType.Footnote,
                                color = OmwColors.InkSecondary,
                            )
                        }
                    }
                }
            }
        }
        Footer {
            CtaButton("Cancel order", kind = CtaKind.Outline) { scope.launch { model.cancelActiveOrder() } }
        }
    }
}

@Composable
private fun InfoPill(id: Int, text: String) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color.White, shape)
            .border(1.dp, OmwColors.Hairline, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(id, 16.dp)
        Text(text, style = OmwType.Footnote.semibold, color = OmwColors.Ink)
    }
}
