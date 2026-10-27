package com.johnyrahul.investmenttracker.core.parsing

import java.time.LocalDate
import java.time.Month

internal val ISIN_REGEX = Regex("""\b(IN[A-Z0-9]{9}\d)\b""")

/** A number as printed on statements: `1,23,456.78`, `(1,234.50)` for negatives, `-12.5`. */
internal val NUMBER_REGEX = Regex("""(?<![\w.])\(?-?\d[\d,]*(?:\.\d+)?\)?(?![\w])""")

internal fun parseNumber(token: String): Double? {
    val negative = token.startsWith("(") || token.startsWith("-")
    val digits = token.trim('(', ')').removePrefix("-").replace(",", "")
    val value = digits.toDoubleOrNull() ?: return null
    return if (negative) -value else value
}

private val MONTHS = Month.entries.associateBy { it.name.take(3) }

private val DATE_NAMED = Regex("""\b(\d{1,2})[-/ ]([A-Za-z]{3})[A-Za-z]*[-/ ,]+(\d{4})\b""")
private val DATE_NUMERIC = Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})\b""")

/** Finds dates like `31-Oct-2026`, `31 October 2026`, `31-10-2026` or `31/10/2026`, in order. */
internal fun findDates(text: String): List<Pair<IntRange, LocalDate>> {
    val found = mutableListOf<Pair<IntRange, LocalDate>>()
    for (m in DATE_NAMED.findAll(text)) {
        val month = MONTHS[m.groupValues[2].uppercase()] ?: continue
        runCatching { LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt()) }
            .onSuccess { found += m.range to it }
    }
    for (m in DATE_NUMERIC.findAll(text)) {
        runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }
            .onSuccess { found += m.range to it }
    }
    return found.sortedBy { it.first.first }
}

internal fun parseDate(text: String): LocalDate? = findDates(text).firstOrNull()?.second

/** Finds "<date> to <date>" and returns the end date of the first such period. */
internal fun findPeriodEnd(lines: List<String>): LocalDate? {
    for (line in lines) {
        val dates = findDates(line)
        for (i in 0 until dates.size - 1) {
            val between = line.substring(dates[i].first.last + 1, dates[i + 1].first.first)
            if (between.trim().lowercase() in setOf("to", "-", "till")) return dates[i + 1].second
        }
    }
    return null
}
