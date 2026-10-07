package com.onmyway.app.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.onmyway.app.model.PaymentSheetConfig
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import kotlinx.coroutines.CompletableDeferred

/** Presents Stripe's PaymentSheet and suspends until it closes. */
class Payments internal constructor(
    private val context: Context,
    private val sheet: PaymentSheet,
    private val relay: ResultRelay,
) {
    suspend fun pay(config: PaymentSheetConfig): PaymentSheetResult {
        PaymentConfiguration.init(context, config.publishableKey)
        val result = CompletableDeferred<PaymentSheetResult>()
        relay.pending = result
        sheet.presentWithPaymentIntent(
            config.paymentIntentClientSecret,
            PaymentSheet.Configuration(
                merchantDisplayName = "On My Way",
                customer = PaymentSheet.CustomerConfiguration(config.customerId, config.customerEphemeralKeySecret),
            ),
        )
        return result.await()
    }

    internal class ResultRelay {
        var pending: CompletableDeferred<PaymentSheetResult>? = null

        fun deliver(result: PaymentSheetResult) {
            pending?.complete(result)
            pending = null
        }
    }
}

@Composable
fun rememberPayments(): Payments {
    val context = LocalContext.current.applicationContext
    val relay = remember { Payments.ResultRelay() }
    val sheet = remember(relay) { PaymentSheet.Builder(relay::deliver) }.build()
    return remember(sheet) { Payments(context, sheet, relay) }
}
