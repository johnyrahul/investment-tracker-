package com.johnyrahul.investmenttracker.core.model

import java.time.LocalDate

/**
 * A tradable instrument: a mutual fund scheme (identified by ISIN / AMFI code)
 * or a listed stock (identified by ISIN / exchange symbol).
 */
data class Instrument(
    val isin: String,
    val name: String,
    val type: AssetType,
    /** AMFI scheme code for mutual funds; used later for daily NAV refresh. */
    val amfiCode: String? = null,
    /** NSE/BSE symbol for stocks; used later for price refresh. */
    val symbol: String? = null,
)

data class Transaction(
    val id: Long = 0,
    val isin: String,
    val platform: Platform,
    val date: LocalDate,
    val type: TransactionType,
    val units: Double,
    val pricePerUnit: Double,
    val amount: Double = units * pricePerUnit,
)

/** Current position in one instrument on one platform, derived from transactions. */
data class Holding(
    val instrument: Instrument,
    val platform: Platform,
    val units: Double,
    val investedAmount: Double,
    val latestPrice: Double?,
) {
    val averageCost: Double get() = if (units > 0) investedAmount / units else 0.0
    val currentValue: Double? get() = latestPrice?.let { it * units }
    val gain: Double? get() = currentValue?.minus(investedAmount)
}
