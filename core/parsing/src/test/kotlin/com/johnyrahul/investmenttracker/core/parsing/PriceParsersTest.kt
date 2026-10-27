package com.johnyrahul.investmenttracker.core.parsing

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PriceParsersTest {

    @Test
    fun `parses AMFI NAV file`() {
        val text = """
            Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Net Asset Value;Date

            Open Ended Schemes(Debt Scheme - Banking and PSU Fund)

            Aditya Birla Sun Life Mutual Fund

            119551;INF209KA12Z1;INF209KA13Z9;Aditya Birla Sun Life Banking & PSU Debt Fund  - DIRECT - IDCW;105.4207;24-Oct-2026
            120465;INF846K01DP8;-;Axis Bluechip Fund - Direct Plan - Growth;55.1234;24-Oct-2026
            100000;INF000000000;-;Wound up fund;N.A.;24-Oct-2026
        """.trimIndent()
        val navs = AmfiNavParser.parse(text.lineSequence()).toList()
        assertEquals(2, navs.size)
        assertEquals(listOf("INF209KA12Z1", "INF209KA13Z9"), navs[0].isins)
        assertEquals(listOf("INF846K01DP8"), navs[1].isins)
        assertEquals("120465", navs[1].schemeCode)
        assertEquals(55.1234, navs[1].nav, 1e-9)
        assertEquals(LocalDate.of(2026, 10, 24), navs[1].date)
    }

    @Test
    fun `parses UDiFF bhavcopy and prefers EQ series`() {
        val text = """
            TradDt,BizDt,Sgmt,Src,FinInstrmTp,FinInstrmId,ISIN,TckrSymb,SctySrs,XpryDt,FininstrmActlXpryDt,StrkPric,OptnTp,FinInstrmNm,OpnPric,HghPric,LwPric,ClsPric,LastPric,PrvsClsgPric
            2026-10-23,2026-10-23,CM,NSE,STK,2885,INE002A01018,RELIANCE,BL,,,,,RELIANCE INDUSTRIES LTD,2800,2900,2790,2801.00,2801,2790
            2026-10-23,2026-10-23,CM,NSE,STK,2885,INE002A01018,RELIANCE,EQ,,,,,RELIANCE INDUSTRIES LTD,2840,2860,2830,2850.25,2851,2845
            2026-10-23,2026-10-23,CM,NSE,STK,1594,INE009A01021,INFY,EQ,,,,,INFOSYS LIMITED,1490,1510,1485,1500.00,1500,1495
        """.trimIndent()
        val prices = BhavcopyParser.parse(text.lineSequence()).associateBy { it.isin }
        assertEquals(2850.25, prices.getValue("INE002A01018").close, 1e-9)
        assertEquals("INFY", prices.getValue("INE009A01021").symbol)
        assertEquals(LocalDate.of(2026, 10, 23), prices.getValue("INE009A01021").date)
    }
}
