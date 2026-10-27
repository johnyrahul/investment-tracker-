package com.johnyrahul.investmenttracker.core.parsing

import com.johnyrahul.investmenttracker.core.model.AssetType
import com.johnyrahul.investmenttracker.core.model.HoldingCategory
import com.johnyrahul.investmenttracker.core.model.Platform
import com.johnyrahul.investmenttracker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CasParserTest {

    private fun fixture(name: String) = javaClass.getResource("/cas/$name")!!.readText()

    @Test
    fun `parses CAMS detailed statement`() {
        val s = CasParser.parse(fixture("cams_detailed.txt"))
        assertEquals(StatementKind.CAMS_KFINTECH, s.kind)
        assertEquals(LocalDate.of(2026, 10, 31), s.asOf)
        assertEquals(setOf(HoldingCategory.MF_FOLIO), s.categories)

        val axis = s.snapshots.single { it.isin == "INF846K01DP8" }
        assertEquals("91012345678/0", axis.account)
        assertEquals(1234.567, axis.units, 1e-9)
        assertEquals(50000.0, axis.costValue!!, 1e-9)
        assertEquals(55.1234, axis.price!!, 1e-9)
        assertEquals(s.asOf, axis.asOf)

        val hdfc = s.snapshots.single { it.isin == "INF179K01WA6" }
        assertEquals("12345678/12", hdfc.account)
        assertEquals(9999.50, hdfc.costValue!!, 1e-9)

        val kotak = s.snapshots.single { it.isin == "INF174K01LS2" }
        assertEquals(0.0, kotak.units, 1e-9)

        assertEquals("Axis Bluechip Fund - Direct Growth", s.instruments.single { it.isin == "INF846K01DP8" }.name)
        assertEquals(
            "HDFC Balanced Advantage Fund - Growth Option - Direct Plan",
            s.instruments.single { it.isin == "INF179K01WA6" }.name,
        )
        assertTrue(s.instruments.all { it.type == AssetType.MUTUAL_FUND })

        val axisTxns = s.transactions.filter { it.isin == "INF846K01DP8" }
        assertEquals(listOf(TransactionType.BUY, TransactionType.SELL, TransactionType.SWITCH_IN), axisTxns.map { it.type })
        assertEquals(25.0, axisTxns[1].units, 1e-9)
        assertEquals(1234.56, axisTxns[1].amount, 1e-9)
        assertEquals(LocalDate.of(2025, 2, 10), axisTxns[0].date)
        assertEquals(5, s.transactions.size)

        val nav = s.prices.single { it.isin == "INF846K01DP8" }
        assertEquals(LocalDate.of(2026, 10, 30), nav.date)
    }

    @Test
    fun `parses NSDL statement`() {
        val s = CasParser.parse(fixture("nsdl.txt"))
        assertEquals(StatementKind.NSDL, s.kind)
        assertEquals(LocalDate.of(2026, 9, 30), s.asOf)
        assertEquals(setOf(HoldingCategory.DEMAT, HoldingCategory.MF_FOLIO), s.categories)

        val reliance = s.snapshots.single { it.isin == "INE002A01018" }
        assertEquals("IN30021412345678", reliance.account)
        assertEquals(Platform.PAYTM_MONEY, reliance.platform)
        assertEquals(50.0, reliance.units, 1e-9)
        assertEquals(2850.25, reliance.price!!, 1e-9)
        assertNull(reliance.costValue)

        val hdfcBank = s.snapshots.single { it.isin == "INE040A01034" }
        assertEquals("1208870087654321", hdfcBank.account)
        assertEquals(Platform.GROWW, hdfcBank.platform)

        val etf = s.snapshots.single { it.isin == "INF204KB14I2" }
        assertEquals(HoldingCategory.DEMAT, etf.category)
        assertEquals(100.0, etf.units, 1e-9)

        val axis = s.snapshots.single { it.isin == "INF846K01DP8" }
        assertEquals(HoldingCategory.MF_FOLIO, axis.category)
        assertEquals("91012345678", axis.account)
        assertEquals(1234.567, axis.units, 1e-9)
        assertEquals(50000.0, axis.costValue!!, 1e-9)
        assertEquals(55.1234, axis.price!!, 1e-9)

        val hdfc = s.snapshots.single { it.isin == "INF179K01WA6" }
        assertEquals("12345678/12", hdfc.account)
        assertEquals(9999.50, hdfc.costValue!!, 1e-9)

        assertEquals(6, s.snapshots.size)
        assertEquals("RELIANCE RELIANCE INDUSTRIES LIMITED", s.instruments.single { it.isin == "INE002A01018" }.name)
        assertEquals("AXIS BLUECHIP FUND - DIRECT GROWTH", s.instruments.single { it.isin == "INF846K01DP8" }.name)
    }

    @Test
    fun `parses CDSL statement`() {
        val s = CasParser.parse(fixture("cdsl.txt"))
        assertEquals(StatementKind.CDSL, s.kind)
        assertEquals(LocalDate.of(2026, 9, 30), s.asOf)

        val hdfcBank = s.snapshots.single { it.isin == "INE040A01034" }
        assertEquals(20.0, hdfcBank.units, 1e-9)
        assertEquals(Platform.GROWW, hdfcBank.platform)
        assertEquals("1208870087654321", hdfcBank.account)
        assertEquals("HDFC BANK LTD", s.instruments.single { it.isin == "INE040A01034" }.name)

        val zomato = s.snapshots.single { it.isin == "INE758T01015" }
        assertEquals(100.0, zomato.units, 1e-9)

        val reliance = s.snapshots.single { it.isin == "INE002A01018" }
        assertEquals(Platform.PAYTM_MONEY, reliance.platform)
        assertEquals("1208160011112222", reliance.account)

        val axis = s.snapshots.single { it.isin == "INF846K01DP8" }
        assertEquals(HoldingCategory.MF_FOLIO, axis.category)
        assertEquals("91012345678", axis.account)
        assertEquals(50000.0, axis.costValue!!, 1e-9)

        val ppfas = s.snapshots.single { it.isin == "INF879O01027" }
        assertEquals("5551234/45", ppfas.account)
        assertEquals(300.25, ppfas.units, 1e-9)
        assertEquals(20000.0, ppfas.costValue!!, 1e-9)
        assertEquals(5, s.snapshots.size)
    }

    @Test(expected = UnsupportedStatementException::class)
    fun `rejects unrelated documents`() {
        CasParser.parse("Electricity bill for October 2026\nAmount due 1,234.00")
    }
}
