package com.johnyrahul.investmenttracker.core.parsing

import java.time.LocalDate

data class AmfiNav(
    val schemeCode: String,
    val isins: List<String>,
    val schemeName: String,
    val nav: Double,
    val date: LocalDate,
)

/**
 * Parses AMFI's daily NAV file (https://www.amfiindia.com/spages/NAVAll.txt):
 * `Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Net Asset Value;Date`
 * interleaved with AMC and category heading lines.
 */
object AmfiNavParser {

    fun parse(lines: Sequence<String>): Sequence<AmfiNav> = lines.mapNotNull { line ->
        val fields = line.split(';')
        if (fields.size < 6) return@mapNotNull null
        val code = fields[0].trim()
        if (code.isEmpty() || !code.all(Char::isDigit)) return@mapNotNull null
        val nav = fields[4].trim().toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        val date = parseDate(fields[5]) ?: return@mapNotNull null
        val isins = listOf(fields[1], fields[2]).map(String::trim).filter { ISIN_REGEX.matches(it) }
        AmfiNav(code, isins, fields[3].trim(), nav, date)
    }
}
