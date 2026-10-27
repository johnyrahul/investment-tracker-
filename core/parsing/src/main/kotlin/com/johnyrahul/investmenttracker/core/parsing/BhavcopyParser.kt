package com.johnyrahul.investmenttracker.core.parsing

import java.time.LocalDate

data class ClosePrice(val isin: String, val symbol: String, val close: Double, val date: LocalDate)

/**
 * Parses the NSE / BSE equity bhavcopy in the common "UDiFF" CSV format both exchanges have used
 * since July 2024 (columns include TradDt, ISIN, TckrSymb, SctySrs, ClsPric).
 */
object BhavcopyParser {

    private val PREFERRED_SERIES = listOf("EQ", "BE", "A", "B")

    fun parse(lines: Sequence<String>): List<ClosePrice> {
        val iterator = lines.iterator()
        if (!iterator.hasNext()) return emptyList()
        val header = iterator.next().removePrefix("﻿").split(',').map { it.trim() }
        val isinCol = header.indexOf("ISIN")
        val closeCol = header.indexOf("ClsPric")
        val dateCol = header.indexOf("TradDt")
        val symbolCol = header.indexOf("TckrSymb")
        val seriesCol = header.indexOf("SctySrs")
        if (isinCol < 0 || closeCol < 0 || dateCol < 0) return emptyList()

        val best = LinkedHashMap<String, Pair<Int, ClosePrice>>()
        for (line in iterator) {
            val f = line.split(',')
            if (f.size <= maxOf(isinCol, closeCol, dateCol)) continue
            val isin = f[isinCol].trim()
            if (!ISIN_REGEX.matches(isin)) continue
            val close = f[closeCol].trim().toDoubleOrNull()?.takeIf { it > 0 } ?: continue
            val date = runCatching { LocalDate.parse(f[dateCol].trim()) }.getOrNull() ?: continue
            val series = f.getOrNull(seriesCol)?.trim().orEmpty()
            val rank = PREFERRED_SERIES.indexOf(series).let { if (it < 0) PREFERRED_SERIES.size else it }
            val price = ClosePrice(isin, f.getOrNull(symbolCol)?.trim().orEmpty(), close, date)
            val existing = best[isin]
            if (existing == null || rank < existing.first) best[isin] = rank to price
        }
        return best.values.map { it.second }
    }
}
