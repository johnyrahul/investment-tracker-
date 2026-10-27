package com.johnyrahul.investmenttracker.core.model

import java.time.LocalDate

/**
 * Works out current holdings from everything that has been imported.
 *
 * Every CAS is a consolidated statement for the PAN, so the newest statement that covers a
 * [HoldingCategory] is authoritative for it: a snapshot older than that statement belongs to a
 * position that has since been exited (it was not listed any more) and is dropped.
 */
object PortfolioResolver {

    private const val EPSILON = 1e-6

    fun resolve(
        instruments: Map<String, Instrument>,
        snapshots: List<PositionSnapshot>,
        /** As-of date of the newest imported statement covering each category. */
        latestStatementByCategory: Map<HoldingCategory, LocalDate>,
        transactions: List<Transaction>,
        latestPrices: Map<String, Double>,
    ): List<Holding> {
        val ledger = HoldingsCalculator.compute(instruments, transactions, emptyMap())
            .associateBy { it.instrument.isin to it.account }

        val fromSnapshots = snapshots
            .groupBy { it.isin to it.account }
            .mapNotNull { (key, history) ->
                val latest = history.maxBy { it.asOf }
                val instrument = instruments[latest.isin] ?: return@mapNotNull null
                val authoritative = latestStatementByCategory[latest.category]
                if (authoritative != null && latest.asOf < authoritative) return@mapNotNull null
                if (latest.units <= EPSILON) return@mapNotNull null
                Holding(
                    instrument = instrument,
                    platform = history.firstOrNull { it.platform != Platform.OTHER }?.platform ?: latest.platform,
                    units = latest.units,
                    investedAmount = latest.costValue ?: ledger[key]?.takeIf { it.units.closeTo(latest.units) }?.investedAmount,
                    latestPrice = latestPrices[latest.isin] ?: latest.price,
                    account = latest.account,
                    asOf = latest.asOf,
                )
            }

        // Positions known only from transactions (no statement snapshot), e.g. manual entries.
        val covered = snapshots.mapTo(HashSet()) { it.isin to it.account }
        val fromLedger = ledger.filterKeys { it !in covered }.values
            .map { it.copy(latestPrice = latestPrices[it.instrument.isin]) }

        return (fromSnapshots + fromLedger).sortedByDescending { it.currentValue ?: it.investedAmount ?: 0.0 }
    }

    private fun Double.closeTo(other: Double) = kotlin.math.abs(this - other) <= maxOf(0.001, 0.001 * other)
}
