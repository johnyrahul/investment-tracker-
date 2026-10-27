package com.johnyrahul.investmenttracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PortfolioResolverTest {

    private val fund = Instrument("INF846K01DP8", "Axis Bluechip Fund", AssetType.MUTUAL_FUND)
    private val stock = Instrument("INE002A01018", "Reliance Industries", AssetType.STOCK)
    private val instruments = listOf(fund, stock).associateBy { it.isin }
    private val sep = LocalDate.of(2026, 9, 30)
    private val oct = LocalDate.of(2026, 10, 31)

    private fun snap(
        isin: String,
        asOf: LocalDate,
        units: Double,
        cost: Double? = null,
        category: HoldingCategory = HoldingCategory.MF_FOLIO,
        platform: Platform = Platform.OTHER,
    ) = PositionSnapshot(isin, "12345", platform, category, asOf, units, cost, price = 10.0)

    @Test
    fun `newest snapshot wins and price falls back to statement price`() {
        val h = PortfolioResolver.resolve(
            instruments,
            listOf(snap(fund.isin, sep, 100.0, 900.0), snap(fund.isin, oct, 120.0, 1100.0)),
            mapOf(HoldingCategory.MF_FOLIO to oct),
            emptyList(),
            emptyMap(),
        ).single()
        assertEquals(120.0, h.units, 1e-9)
        assertEquals(1100.0, h.investedAmount!!, 1e-9)
        assertEquals(1200.0, h.currentValue!!, 1e-9)
    }

    @Test
    fun `position missing from newer statement of same category is dropped`() {
        val holdings = PortfolioResolver.resolve(
            instruments,
            listOf(
                snap(fund.isin, sep, 100.0),
                snap(stock.isin, oct, 5.0, category = HoldingCategory.DEMAT),
            ),
            mapOf(HoldingCategory.MF_FOLIO to oct, HoldingCategory.DEMAT to oct),
            emptyList(),
            emptyMap(),
        )
        assertEquals(listOf(stock.isin), holdings.map { it.instrument.isin })
    }

    @Test
    fun `older category is unaffected by newer statement of other category`() {
        val holdings = PortfolioResolver.resolve(
            instruments,
            listOf(snap(fund.isin, sep, 100.0), snap(stock.isin, oct, 5.0, category = HoldingCategory.DEMAT)),
            mapOf(HoldingCategory.MF_FOLIO to sep, HoldingCategory.DEMAT to oct),
            emptyList(),
            mapOf(stock.isin to 2900.0),
        )
        assertEquals(2, holdings.size)
        assertEquals(14500.0, holdings.first().currentValue!!, 1e-9)
    }

    @Test
    fun `cost comes from transactions when the statement omits it`() {
        val txns = listOf(
            Transaction(isin = fund.isin, platform = Platform.OTHER, account = "12345", date = sep, type = TransactionType.BUY, units = 100.0, pricePerUnit = 9.0),
        )
        val h = PortfolioResolver.resolve(instruments, listOf(snap(fund.isin, oct, 100.0)), emptyMap(), txns, emptyMap()).single()
        assertEquals(900.0, h.investedAmount!!, 1e-9)

        val unknown = PortfolioResolver.resolve(instruments, listOf(snap(fund.isin, oct, 150.0)), emptyMap(), txns, emptyMap()).single()
        assertNull(unknown.investedAmount)
        assertNull(unknown.gain)
    }

    @Test
    fun `zero balance and known platform`() {
        assertTrue(
            PortfolioResolver.resolve(instruments, listOf(snap(fund.isin, oct, 0.0)), emptyMap(), emptyList(), emptyMap()).isEmpty(),
        )
        val h = PortfolioResolver.resolve(
            instruments,
            listOf(snap(stock.isin, sep, 5.0, platform = Platform.GROWW, category = HoldingCategory.DEMAT), snap(stock.isin, oct, 6.0, category = HoldingCategory.DEMAT)),
            emptyMap(),
            emptyList(),
            emptyMap(),
        ).single()
        assertEquals(Platform.GROWW, h.platform)
    }
}
