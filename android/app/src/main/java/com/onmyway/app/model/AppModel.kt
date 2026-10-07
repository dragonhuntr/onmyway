package com.onmyway.app.model

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant

enum class AppTab { Home, Orders, Run, Earnings }

/** Screens pushed on the Run tab. */
sealed interface RunRoute {
    data class Request(val request: DeliveryRequest) : RunRoute
    data class Active(val order: Order) : RunRoute
}

/** Chat shown full screen over the tabs. */
data class ChatTarget(val orderId: String, val title: String)

private const val TOKEN_KEY = "accountToken"

class AppModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("account", Context.MODE_PRIVATE)

    var selectedTab by mutableStateOf(AppTab.Home)

    /** Signed-in account from Cloudflare D1. Null shows the login screen. */
    var session by mutableStateOf<SessionUser?>(null)
        private set
    private var token: String? = prefs.getString(TOKEN_KEY, null)
    /** True while a saved token is being checked on launch. */
    var isRestoringSession by mutableStateOf(token != null)
        private set

    /** Message to show in an alert, e.g. when an order expires. */
    var notice by mutableStateOf<String?>(null)
    /** Open chat, if any. */
    var chat by mutableStateOf<ChatTarget?>(null)

    // Customer side
    var destination by mutableStateOf<Destination?>(null)
        private set
    var buildings by mutableStateOf<List<Building>>(emptyList())
        private set
    var runnersHeadingYourWay by mutableIntStateOf(0)
        private set
    var restaurants by mutableStateOf<List<Restaurant>>(emptyList())
        private set
    /** Restaurant whose Transact ordering flow is currently presented. */
    var orderingFrom by mutableStateOf<Restaurant?>(null)
    var activeOrder by mutableStateOf<Order?>(null)
        private set
    /** Set when the active order is delivered, to present the confirmation screen. */
    var deliveredOrder by mutableStateOf<Order?>(null)
        private set
    private var orderWatch: Job? = null

    // Runner side
    var isAvailable by mutableStateOf(false)
        private set
    var trip by mutableStateOf<Trip?>(null)
        private set
    var activeDelivery by mutableStateOf<Order?>(null)
        private set
    var requests by mutableStateOf<List<DeliveryRequest>>(emptyList())
        private set
    var recentDeliveries by mutableStateOf<List<CompletedDelivery>>(emptyList())
        private set
    var cashOutBalanceCents by mutableIntStateOf(0)
        private set
    val earnings = mutableStateMapOf<EarningsPeriod, Earnings>()
    /** Run tab navigation stack; the tab bar hides while it is non-empty. */
    val runPath = mutableStateListOf<RunRoute>()

    init {
        if (token != null) viewModelScope.launch { restoreSession() }
    }

    // Auth

    private suspend fun restoreSession() {
        val token = token ?: return
        try {
            session = Api.currentAccount(token).sessionUser
            loadSignedIn()
        } catch (e: ApiException) {
            clearToken()
        } finally {
            isRestoringSession = false
        }
    }

    suspend fun logIn(username: String, password: String) {
        store(Api.login(username, password))
    }

    suspend fun register(email: String, firstName: String, lastName: String, username: String, password: String) {
        store(Api.register(email, firstName, lastName, username, password))
    }

    fun signOut() {
        val token = token
        viewModelScope.launch { runCatching { Api.send<Empty>("POST", "logout", token = token) } }
        orderWatch?.cancel()
        session = null
        activeOrder = null
        deliveredOrder = null
        activeDelivery = null
        orderingFrom = null
        chat = null
        runPath.clear()
        selectedTab = AppTab.Home
        clearToken()
    }

    private suspend fun store(account: Api.Account) {
        token = account.token
        prefs.edit { putString(TOKEN_KEY, account.token) }
        session = account.sessionUser
        loadSignedIn()
    }

    private fun clearToken() {
        token = null
        prefs.edit { remove(TOKEN_KEY) }
    }

    private suspend fun loadSignedIn() {
        listOf(
            viewModelScope.async { loadHome() },
            viewModelScope.async { loadActiveOrder() },
            viewModelScope.async { refreshRunner() },
        ).awaitAll()
    }

    /** Calls the API as the signed-in user; a 401 signs out. */
    private suspend inline fun <reified T> call(
        method: String,
        path: String,
        body: Map<String, Any?>? = null,
        query: Map<String, String> = emptyMap(),
    ): T {
        try {
            return Api.send(method, path, body, query, token)
        } catch (e: ApiException) {
            if (e.status == 401) signOut()
            throw e
        }
    }

    /** Like [call], but returns null on any failure. */
    private suspend inline fun <reified T> tryCall(
        method: String,
        path: String,
        body: Map<String, Any?>? = null,
        query: Map<String, String> = emptyMap(),
    ): T? = try {
        call<T>(method, path, body, query)
    } catch (e: ApiException) {
        null
    }

    // Places

    @Serializable
    private data class HomeResponse(
        val destination: Destination? = null,
        val runnersHeadingYourWay: Int,
        val restaurants: List<Restaurant>,
    )

    suspend fun loadHome() {
        val home = tryCall<HomeResponse>("GET", "home") ?: return
        destination = home.destination
        runnersHeadingYourWay = home.runnersHeadingYourWay
        restaurants = home.restaurants
    }

    @Serializable
    private data class BuildingsResponse(val buildings: List<Building>)

    suspend fun loadBuildings() {
        if (buildings.isNotEmpty()) return
        tryCall<BuildingsResponse>("GET", "buildings")?.let { buildings = it.buildings }
    }

    @Serializable
    private data class DestinationResponse(val destination: Destination)

    suspend fun setDestination(buildingId: String, room: String, note: String) {
        val response = call<DestinationResponse>(
            "PUT", "me/destination",
            body = mapOf("buildingId" to buildingId, "room" to room, "note" to note),
        )
        destination = response.destination
        loadHome()
    }

    // Customer actions

    @Serializable
    private data class OrderResponse(val order: Order, val payment: PaymentSheetConfig? = null)

    /** Creates the order. Returns a PaymentSheet config when the delivery fee + tip still has to be paid. */
    suspend fun createOrder(
        restaurant: Restaurant,
        number: String,
        nameOnOrder: String,
        readyAt: Instant,
        tip: Tip,
    ): PaymentSheetConfig? {
        val response = call<OrderResponse>(
            "POST", "orders",
            body = mapOf(
                "restaurantId" to restaurant.id,
                "orderNumber" to number,
                "nameOnOrder" to nameOnOrder,
                "readyAt" to readyAt.iso(),
                "tipCents" to tip.cents,
            ),
        )
        activeOrder = response.order
        if (response.payment == null) watchActiveOrder()
        return response.payment
    }

    /** PaymentSheet finished; the server checks with Stripe and opens the order to runners. */
    suspend fun confirmPayment() {
        val order = activeOrder ?: return
        val response = call<OrderResponse>("POST", "orders/${order.id}/payment")
        apply(response.order)
        watchActiveOrder()
    }

    @Serializable
    private data class ActiveOrderResponse(val order: Order? = null)

    suspend fun loadActiveOrder() {
        val response = tryCall<ActiveOrderResponse>("GET", "orders/active") ?: return
        activeOrder = response.order
        val order = response.order ?: return
        if (order.status != Order.Status.AwaitingPayment) {
            if (order.status != Order.Status.Matching) selectedTab = AppTab.Orders
            watchActiveOrder()
        }
    }

    /** Polls the active order until it's delivered, cancelled, or expired. */
    private fun watchActiveOrder() {
        orderWatch?.cancel()
        orderWatch = viewModelScope.launch {
            while (isActive) {
                delay(3_000)
                val id = activeOrder?.id ?: return@launch
                tryCall<OrderResponse>("GET", "orders/$id")?.let { apply(it.order) }
            }
        }
    }

    private fun apply(order: Order) {
        val wasWaiting = activeOrder?.let {
            it.status == Order.Status.Matching || it.status == Order.Status.AwaitingPayment
        } ?: false
        when (order.status) {
            Order.Status.Accepted, Order.Status.PickedUp -> {
                activeOrder = order
                if (wasWaiting) {
                    // A runner took it: close the ordering flow and track in Orders.
                    orderingFrom = null
                    selectedTab = AppTab.Orders
                }
            }
            Order.Status.Delivered -> {
                orderWatch?.cancel()
                activeOrder = null
                orderingFrom = null
                if (chat?.orderId == order.id) chat = null
                deliveredOrder = order
            }
            Order.Status.Cancelled, Order.Status.Expired -> {
                orderWatch?.cancel()
                activeOrder = null
                orderingFrom = null
                if (chat?.orderId == order.id) chat = null
                notice = order.cancelReason?.let { "$it. Your card wasn’t charged." }
            }
            Order.Status.AwaitingPayment, Order.Status.Matching -> activeOrder = order
        }
    }

    suspend fun cancelActiveOrder() {
        val order = activeOrder ?: run {
            orderingFrom = null
            return
        }
        try {
            apply(call<OrderResponse>("POST", "orders/${order.id}/cancel").order)
        } catch (e: ApiException) {
            notice = e.message
        }
    }

    @Serializable
    private data class RatingResponse(val tip: TipInfo? = null, val payment: PaymentSheetConfig? = null) {
        @Serializable
        data class TipInfo(val id: String)
    }

    /** Rates the runner. Returns the tip id and PaymentSheet config when an extra tip needs paying. */
    suspend fun rate(order: Order, stars: Int, tags: List<String>, extraTipCents: Int): Pair<String, PaymentSheetConfig>? {
        val response = call<RatingResponse>(
            "POST", "orders/${order.id}/rating",
            body = mapOf("stars" to stars, "tags" to tags, "extraTipCents" to extraTipCents),
        )
        val tip = response.tip ?: return null
        val payment = response.payment ?: return null
        return tip.id to payment
    }

    suspend fun confirmTip(tipId: String) {
        call<Empty>("POST", "tips/$tipId/payment")
    }

    fun finishDelivered(orderAgain: Boolean) {
        val restaurant = restaurants.firstOrNull { it.id == deliveredOrder?.restaurant?.id }
        deliveredOrder = null
        viewModelScope.launch { loadHome() }
        if (orderAgain && restaurant != null) {
            selectedTab = AppTab.Home
            orderingFrom = restaurant
        }
    }

    // Chat

    @Serializable
    private data class MessagesResponse(val messages: List<ChatMessage>)

    suspend fun messages(orderId: String, after: Instant?): List<ChatMessage> =
        call<MessagesResponse>(
            "GET", "orders/$orderId/messages",
            query = after?.let { mapOf("after" to it.iso()) } ?: emptyMap(),
        ).messages

    @Serializable
    private data class MessageResponse(val message: ChatMessage)

    suspend fun send(body: String, orderId: String): ChatMessage =
        call<MessageResponse>("POST", "orders/$orderId/messages", body = mapOf("body" to body)).message

    suspend fun report(orderId: String, reason: String, details: String) {
        call<Empty>("POST", "orders/$orderId/report", body = mapOf("reason" to reason, "details" to details))
    }

    // Runner actions

    suspend fun refreshRunner() {
        val status = tryCall<RunnerStatus>("GET", "runner") ?: return
        isAvailable = status.available
        trip = status.trip
        activeDelivery = status.activeDelivery
        cashOutBalanceCents = status.balanceCents
    }

    @Serializable
    private data class AvailabilityResponse(val available: Boolean)

    suspend fun setAvailable(available: Boolean) {
        isAvailable = available
        tryCall<AvailabilityResponse>("PUT", "runner/status", body = mapOf("available" to available))?.let {
            isAvailable = it.available
        }
    }

    @Serializable
    private data class RequestsResponse(val trip: Trip? = null, val requests: List<DeliveryRequest>)

    suspend fun refreshRequests(filter: String) {
        val response = tryCall<RequestsResponse>("GET", "runner/requests", query = mapOf("filter" to filter)) ?: return
        trip = response.trip
        requests = response.requests
    }

    suspend fun accept(request: DeliveryRequest): Order {
        val response = call<OrderResponse>("POST", "orders/${request.id}/accept", body = emptyMap())
        requests = requests.filterNot { it.id == request.id }
        activeDelivery = response.order
        return response.order
    }

    suspend fun confirmPickup(order: Order): Order {
        val response = call<OrderResponse>("POST", "orders/${order.id}/pickup")
        activeDelivery = response.order
        return response.order
    }

    suspend fun confirmDropoff(order: Order) {
        call<OrderResponse>("POST", "orders/${order.id}/deliver")
        activeDelivery = null
        // The server credits the runner right after capture; give it a moment.
        delay(1_000)
        refreshRunner()
        loadEarnings(EarningsPeriod.ThisWeek)
        loadDeliveries()
    }

    /** Hands an accepted order back to other runners (before pickup only). */
    suspend fun release(order: Order) {
        call<Empty>("POST", "orders/${order.id}/release")
        activeDelivery = null
    }

    suspend fun reportLocation(latitude: Double, longitude: Double) {
        tryCall<Empty>("POST", "runner/location", body = mapOf("lat" to latitude, "lng" to longitude))
    }

    @Serializable
    private data class TripResponse(val trip: Trip)

    suspend fun saveTrip(id: String?, from: String, to: String, leaveAt: Instant, note: String) {
        val body = mapOf("fromBuildingId" to from, "toBuildingId" to to, "leaveAt" to leaveAt.iso(), "note" to note)
        val response = if (id != null) {
            call<TripResponse>("PUT", "trips/$id", body = body)
        } else {
            call<TripResponse>("POST", "trips", body = body)
        }
        trip = response.trip
    }

    suspend fun deleteTrip(trip: Trip) {
        call<Empty>("DELETE", "trips/${trip.id}")
        refreshRunner()
    }

    // Earnings

    suspend fun loadEarnings(period: EarningsPeriod) {
        val response = tryCall<Earnings>("GET", "earnings", query = mapOf("week" to period.query)) ?: return
        earnings[period] = response
        cashOutBalanceCents = response.balanceCents
    }

    @Serializable
    private data class DeliveriesResponse(val deliveries: List<CompletedDelivery>)

    suspend fun loadDeliveries() {
        tryCall<DeliveriesResponse>("GET", "deliveries")?.let { recentDeliveries = it.deliveries }
    }

    @Serializable
    private data class PayoutResponse(val balanceCents: Int)

    @Serializable
    private data class Link(val url: String)

    /** Cashes out the balance. Returns a Stripe onboarding URL instead when payouts aren't set up yet. */
    suspend fun cashOut(): String? = try {
        cashOutBalanceCents = call<PayoutResponse>("POST", "payouts").balanceCents
        null
    } catch (e: ApiException) {
        if (e.code != "payout_account_missing" && e.code != "payout_account_incomplete") throw e
        call<Link>("POST", "runner/payout-account").url
    }

    fun earnings(period: EarningsPeriod): List<DailyEarning> {
        val letters = listOf("M", "T", "W", "T", "F", "S", "S")
        return earnings[period]?.days.orEmpty().mapIndexed { index, day ->
            DailyEarning(letters[index % 7], index, day.cents / 100.0)
        }
    }
}
