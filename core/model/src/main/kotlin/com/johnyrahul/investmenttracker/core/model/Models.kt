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
    /** AMFI scheme code for mutual funds; filled in by the daily NAV refresh. */
    val amfiCode: String? = null,
    /** NSE/BSE symbol for stocks; filled in by the daily price refresh. */
    val symbol: String? = null,
)

data class Transaction(
    val id: Long = 0,
    val isin: String,
    val platform: Platform,
    /** Folio number for mutual fund folios, DP/BO id for demat accounts. */
    val account: String = "",
    val date: LocalDate,
    val type: TransactionType,
    val units: Double,
    val pricePerUnit: Double,
    val amount: Double = units * pricePerUnit,
)

/**
 * Units held in one instrument in one account as reported by a statement on [asOf].
 * Statements always report closing balances, so the newest snapshot is the source of truth
 * for units; transactions are kept for XIRR and tax insights.
 */
data class PositionSnapshot(
    val isin: String,
    val account: String,
    val platform: Platform,
    val category: HoldingCategory,
    val asOf: LocalDate,
    val units: Double,
    /** Total purchase cost of the units, when the statement reports it. */
    val costValue: Double?,
    /** NAV / market price on [asOf], when the statement reports it. */
    val price: Double?,
)

data class PricePoint(val isin: String, val date: LocalDate, val price: Double)

/** Current position in one instrument in one account. */
data class Holding(
    val instrument: Instrument,
    val platform: Platform,
    val units: Double,
    /** Null when no imported statement reports the purchase cost. */
    val investedAmount: Double?,
    val latestPrice: Double?,
    val account: String = "",
    val asOf: LocalDate? = null,
) {
    val averageCost: Double? get() = investedAmount?.let { if (units > 0) it / units else 0.0 }
    val currentValue: Double? get() = latestPrice?.let { it * units }
    val gain: Double? get() = currentValue?.let { value -> investedAmount?.let { value - it } }
}
