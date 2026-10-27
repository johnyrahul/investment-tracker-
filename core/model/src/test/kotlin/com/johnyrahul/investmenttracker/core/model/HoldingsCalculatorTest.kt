package com.johnyrahul.investmenttracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HoldingsCalculatorTest {

    private val fund = Instrument("INF000000001", "Sample Flexi Cap Fund", AssetType.MUTUAL_FUND)
    private val instruments = mapOf(fund.isin to fund)

    private fun txn(day: Int, type: TransactionType, units: Double, price: Double) =
        Transaction(
            isin = fund.isin,
            platform = Platform.GROWW,
            date = LocalDate.of(2025, 1, day),
            type = type,
            units = units,
            pricePerUnit = price,
        )

    @Test
    fun `buys accumulate units and cost`() {
        val holdings = HoldingsCalculator.compute(
            instruments,
            listOf(txn(1, TransactionType.BUY, 10.0, 100.0), txn(2, TransactionType.BUY, 10.0, 120.0)),
            mapOf(fund.isin to 130.0),
        )
        val h = holdings.single()
        assertEquals(20.0, h.units, 1e-9)
        assertEquals(2200.0, h.investedAmount!!, 1e-9)
        assertEquals(110.0, h.averageCost!!, 1e-9)
        assertEquals(400.0, h.gain!!, 1e-9)
    }

    @Test
    fun `partial sell reduces cost proportionally`() {
        val h = HoldingsCalculator.compute(
            instruments,
            listOf(txn(1, TransactionType.BUY, 10.0, 100.0), txn(5, TransactionType.SELL, 4.0, 150.0)),
            emptyMap(),
        ).single()
        assertEquals(6.0, h.units, 1e-9)
        assertEquals(600.0, h.investedAmount!!, 1e-9)
    }

    @Test
    fun `fully exited positions are dropped`() {
        val holdings = HoldingsCalculator.compute(
            instruments,
            listOf(txn(1, TransactionType.BUY, 10.0, 100.0), txn(5, TransactionType.SELL, 10.0, 150.0)),
            emptyMap(),
        )
        assertTrue(holdings.isEmpty())
    }
}
