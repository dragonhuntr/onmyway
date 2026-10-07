package com.onmyway.app.model

import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.onmyway.app.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Signed-in person. `id` is the D1 user id, and later the Auth0 `sub`. */
data class SessionUser(
    val id: String,
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
) {
    /** "Evan B." */
    val shortName: String
        get() = lastName.firstOrNull()?.let { "$firstName ${it.uppercaseChar()}." } ?: firstName
}

/** A campus building, as a reference inside other responses. */
@Serializable
data class Place(val id: String, val name: String)

@Serializable
data class Building(val id: String, val name: String)

@Serializable
data class Restaurant(
    val id: String,
    val name: String,
    /** Location ID in Transact web ordering (`weborder.transactcampus.com/237/<id>`). */
    val transactId: Int,
    val building: Place? = null,
    val opensMinute: Int,
    val closesMinute: Int? = null,
    val distanceMiles: Double? = null,
    val walkMinutes: Int? = null,
) {
    val orderingUrl: String get() = "https://weborder.transactcampus.com/237/$transactId"
    val hours: Hours get() = Hours(opensMinute, closesMinute)

    @get:DrawableRes
    val logo: Int
        get() = when (id) {
            "clarks" -> R.drawable.logo_clarks
            "paws" -> R.drawable.logo_paws
            "brunos" -> R.drawable.logo_brunos
            else -> R.drawable.building
        }

    /** Background behind the logo; matches the logo image's own background. */
    val tile: Color get() = if (id == "brunos") Color(0xFF0B2C52) else Color.White

    val distance: String
        get() {
            val miles = distanceMiles ?: return building?.name ?: "On campus"
            return if (miles < 0.1) "Next door" else "${miles.plain()} mi"
        }

    val eta: String get() = walkMinutes?.let { "${it + 3}–${it + 8} min" } ?: "~10 min"

    /** Daily opening hours, as minutes after midnight. `closes` is null when unknown. */
    data class Hours(val opens: Int, val closes: Int?) {
        fun isOpen(at: LocalTime = LocalTime.now()): Boolean {
            val minute = at.hour * 60 + at.minute
            return minute >= opens && minute < (closes ?: (24 * 60))
        }

        fun status(at: LocalTime = LocalTime.now()): String {
            if (isOpen(at)) {
                val closes = closes ?: return "Open now"
                return "Open · ${format(opens)} – ${format(closes)}"
            }
            val minute = at.hour * 60 + at.minute
            return if (minute < opens) "Opens at ${format(opens)}" else "Closed · Opens ${format(opens)}"
        }

        private fun format(minutes: Int): String {
            val time = LocalTime.of(minutes / 60 % 24, minutes % 60)
            return time.format(if (minutes % 60 == 0) hourOnly else hourMinute)
        }

        private companion object {
            val hourOnly: DateTimeFormatter = DateTimeFormatter.ofPattern("h a", Locale.US)
            val hourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        }
    }
}

@Serializable
data class Destination(
    val buildingId: String,
    val building: String,
    val room: String,
    val note: String,
) {
    val short: String get() = if (room.isEmpty()) building else "$building, ${room.replace("Room", "Rm")}"
    val full: String get() = if (room.isEmpty()) building else "$building · $room"
}

enum class Tip(val cents: Int) {
    None(0), Half(50), One(100), Two(200);

    val label: String get() = if (this == None) "None" else cents.usd
}

/** A student's order: food paid on Transact, delivery handed to a runner. */
@Serializable
data class Order(
    val id: String,
    /** "customer" or "runner": which side of the order the signed-in user is on. */
    val role: String,
    val status: Status,
    val number: String,
    val nameOnOrder: String,
    @Serializable(InstantSerializer::class) val readyAt: Instant? = null,
    val restaurant: OrderRestaurant,
    val destination: Destination,
    val walkMinutes: Int,
    val deliveryFeeCents: Int,
    val tipCents: Int,
    val extraTipCents: Int = 0,
    val payoutCents: Int = 0,
    val etaMinutes: Int? = null,
    val runner: Runner? = null,
    val customer: Customer? = null,
    val rating: Rating? = null,
    @Serializable(InstantSerializer::class) val createdAt: Instant,
    @Serializable(InstantSerializer::class) val acceptedAt: Instant? = null,
    @Serializable(InstantSerializer::class) val pickedUpAt: Instant? = null,
    @Serializable(InstantSerializer::class) val deliveredAt: Instant? = null,
    @Serializable(InstantSerializer::class) val cancelledAt: Instant? = null,
    val cancelReason: String? = null,
) {
    @Serializable
    enum class Status {
        @SerialName("awaiting_payment") AwaitingPayment,
        @SerialName("matching") Matching,
        @SerialName("accepted") Accepted,
        @SerialName("picked_up") PickedUp,
        @SerialName("delivered") Delivered,
        @SerialName("cancelled") Cancelled,
        @SerialName("expired") Expired;

        val isActive: Boolean get() = this in setOf(AwaitingPayment, Matching, Accepted, PickedUp)
    }

    @Serializable
    data class OrderRestaurant(val id: String, val name: String, val building: Place)

    @Serializable
    data class Customer(val id: String, val name: String, val orders: Int)

    @Serializable
    data class Rating(val stars: Int, val tags: List<String>)

    val dueNowCents: Int get() = deliveryFeeCents + tipCents
}

@Serializable
data class Runner(
    val id: String,
    val name: String,
    val firstName: String,
    val rating: Double? = null,
    val deliveries: Int,
    val blurb: String = "",
    val routeNote: String? = null,
    val location: Location? = null,
) {
    @Serializable
    data class Location(
        val lat: Double,
        val lng: Double,
        @Serializable(InstantSerializer::class) val updatedAt: Instant,
    )

    /** "4.9 · 38 deliveries · Junior, CS" */
    val summary: String
        get() = listOfNotNull(
            rating?.plain(),
            if (deliveries == 0) "New runner" else "$deliveries deliveries",
            blurb.ifEmpty { null },
        ).joinToString(" · ")
}

/** An order a runner can pick up along their route. */
@Serializable
data class DeliveryRequest(
    val id: String,
    val restaurant: Order.OrderRestaurant,
    val destination: Destination,
    val number: String,
    val nameOnOrder: String,
    @Serializable(InstantSerializer::class) val readyAt: Instant? = null,
    val readyNow: Boolean,
    @Serializable(InstantSerializer::class) val dropoffBy: Instant,
    val detourMinutes: Int,
    val detourMiles: Double,
    val onRoute: Boolean,
    val payoutCents: Int,
    val customer: Customer,
) {
    @Serializable
    data class Customer(val name: String, val orders: Int)

    val readyText: String get() = if (readyNow) "Ready now" else "Ready ${readyAt?.shortTime ?: "soon"}"
}

/** A walk a runner is already taking. */
@Serializable
data class Trip(
    val id: String,
    val from: Place,
    val to: Place,
    @Serializable(InstantSerializer::class) val leaveAt: Instant,
    val note: String = "",
    val walkMinutes: Int,
)

@Serializable
data class RunnerStatus(
    val available: Boolean,
    val trip: Trip? = null,
    val activeDelivery: Order? = null,
    val balanceCents: Int,
)

@Serializable
data class CompletedDelivery(
    val id: String,
    val restaurant: String,
    val building: String,
    val customer: String,
    @Serializable(InstantSerializer::class) val deliveredAt: Instant,
    val amountCents: Int,
    val tipCents: Int,
    val stars: Int? = null,
) {
    val route: String get() = "$restaurant → $building"
    val detail: String
        get() = "${
            DateUtils.getRelativeTimeSpanString(deliveredAt.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        } · $customer"
    val rating: String
        get() {
            val stars = stars ?: return "Awaiting rating"
            return if (tipCents > 0) "$stars★ · ${tipCents.usd} tip" else "$stars★"
        }
}

enum class EarningsPeriod(val label: String, val query: String) {
    ThisWeek("This week", "this"), LastWeek("Last week", "last")
}

@Serializable
data class Earnings(
    val start: String,
    val end: String,
    val days: List<Day>,
    val totalCents: Int,
    val changePercent: Int? = null,
    val deliveries: Int,
    val walkingMinutes: Int,
    val averageCents: Int,
    val balanceCents: Int,
    val payoutsEnabled: Boolean,
) {
    @Serializable
    data class Day(val date: String, val cents: Int)
}

data class DailyEarning(val day: String, val index: Int, val amount: Double)

@Serializable
data class ChatMessage(
    val id: String,
    val body: String,
    @Serializable(InstantSerializer::class) val createdAt: Instant,
    val fromMe: Boolean,
)

/** What Stripe's PaymentSheet needs; returned when an order or extra tip has to be paid. */
@Serializable
data class PaymentSheetConfig(
    val paymentIntentClientSecret: String,
    val customerId: String,
    val customerEphemeralKeySecret: String,
    val publishableKey: String,
)

/** Cents as dollars: 150 → "$1.50". */
val Int.usd: String get() = com.onmyway.app.ui.theme.formatUsd(this / 100.0)

private val shortTimeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** "9:50 AM" */
val Instant.shortTime: String get() = atZone(ZoneId.systemDefault()).format(shortTimeFormat)

/** Formats like Swift's `Double.formatted()`: no trailing zeros ("4.9", "5", "0.1"). */
fun Double.plain(): String =
    if (this == Math.floor(this)) toLong().toString() else toBigDecimal().stripTrailingZeros().toPlainString()
