package com.johnyrahul.investmenttracker.core.model

/**
 * Folds a transaction history into holdings using average-cost accounting.
 * Lot-level (FIFO) tracking for capital-gains tax will be added with the tax insights.
 */
object HoldingsCalculator {

    private const val EPSILON = 1e-6

    fun compute(
        instruments: Map<String, Instrument>,
        transactions: List<Transaction>,
        latestPrices: Map<String, Double>,
    ): List<Holding> =
        transactions
            .groupBy { it.isin to it.platform }
            .mapNotNull { (key, txns) ->
                val (isin, platform) = key
                val instrument = instruments[isin] ?: return@mapNotNull null
                var units = 0.0
                var invested = 0.0
                for (txn in txns.sortedBy { it.date }) {
                    if (txn.type.unitsSign > 0) {
                        units += txn.units
                        invested += txn.amount
                    } else if (units > EPSILON) {
                        val sold = minOf(txn.units, units)
                        invested -= invested * (sold / units)
                        units -= sold
                    }
                }
                if (units <= EPSILON) return@mapNotNull null
                Holding(instrument, platform, units, invested, latestPrices[isin])
            }
            .sortedByDescending { it.currentValue ?: it.investedAmount }
}
