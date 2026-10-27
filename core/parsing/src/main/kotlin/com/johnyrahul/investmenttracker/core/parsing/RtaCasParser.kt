package com.johnyrahul.investmenttracker.core.parsing

import com.johnyrahul.investmenttracker.core.model.AssetType
import com.johnyrahul.investmenttracker.core.model.HoldingCategory
import com.johnyrahul.investmenttracker.core.model.Instrument
import com.johnyrahul.investmenttracker.core.model.Platform
import com.johnyrahul.investmenttracker.core.model.PositionSnapshot
import com.johnyrahul.investmenttracker.core.model.PricePoint
import com.johnyrahul.investmenttracker.core.model.Transaction
import com.johnyrahul.investmenttracker.core.model.TransactionType
import java.time.LocalDate
import kotlin.math.abs

/**
 * Parses the detailed Consolidated Account Statement mailed by CAMS / KFintech, which lists every
 * mutual fund folio with its full transaction history and closing balance.
 */
internal object RtaCasParser {

    private val FOLIO = Regex("""Folio\s*No\.?\s*:?\s*(\w+(?:\s*/\s*\w+)?)""", RegexOption.IGNORE_CASE)
    private val ISIN_LABEL = Regex("""ISIN\s*:?\s*(IN[A-Z0-9]{9}\d)?""")
    private val SCHEME_CODE_PREFIX = Regex("""^[A-Z0-9]{1,10}-""")
    private const val NUM = """\(?-?[\d,]+\.\d+\)?"""
    private val TRANSACTION = Regex(
        """^(\d{2}-[A-Za-z]{3}-\d{4})\s+(.*?)\s+($NUM)\s+($NUM)\s+($NUM)\s+($NUM)\s*$""",
    )
    private val CLOSING = Regex("""Closing\s+Unit\s+Balance\s*:?\s*($NUM|[\d,]+)""", RegexOption.IGNORE_CASE)
    private val NAV = Regex("""NAV\s+on\s+(\S+)\s*:?\s*(?:INR|Rs\.?|₹)?\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val COST = Regex("""Cost\s+Value\s*:?\s*(?:INR|Rs\.?|₹)?\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)

    fun looksLikeRtaCas(text: String): Boolean =
        text.contains("Closing Unit Balance", ignoreCase = true) && FOLIO.containsMatchIn(text)

    fun parse(lines: List<String>): ParsedStatement {
        val asOf = findPeriodEnd(lines.take(60))
            ?: throw UnsupportedStatementException("Couldn't find the statement period in this CAS")

        val instruments = LinkedHashMap<String, Instrument>()
        val snapshots = mutableListOf<PositionSnapshot>()
        val transactions = mutableListOf<Transaction>()
        val prices = mutableListOf<PricePoint>()

        var folio: String? = null
        var isin: String? = null

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            FOLIO.find(line)?.let { folio = it.groupValues[1].replace(" ", "") }

            val isinLabel = ISIN_LABEL.find(line)
            if (isinLabel != null && line.contains("ISIN")) {
                var code = isinLabel.groups[1]?.value
                // Long scheme names push the ISIN code to the start of the next line.
                if (code == null) code = lines.getOrNull(i + 1)?.let { ISIN_REGEX.find(it.take(20))?.value }
                if (code != null) {
                    isin = code
                    var name = line.substring(0, isinLabel.range.first)
                    val previous = lines.getOrNull(i - 1)?.trim().orEmpty()
                    if (!SCHEME_CODE_PREFIX.containsMatchIn(name) && SCHEME_CODE_PREFIX.containsMatchIn(previous) &&
                        FOLIO.find(previous) == null
                    ) {
                        name = "$previous $name"
                    }
                    instruments.putIfAbsent(code, Instrument(code, cleanSchemeName(name), AssetType.fromIsin(code)))
                }
                i++
                continue
            }

            val currentIsin = isin
            val currentFolio = folio
            if (currentIsin != null && currentFolio != null) {
                TRANSACTION.find(line)?.let { m ->
                    transactionFrom(m, currentIsin, currentFolio)?.let(transactions::add)
                }
                CLOSING.find(line)?.let { m ->
                    // NAV, cost and value usually share the closing line but can wrap onto the next two.
                    val window = (i until minOf(i + 3, lines.size)).joinToString(" ") { lines[it] }
                    val units = parseNumber(m.groupValues[1]) ?: 0.0
                    val nav = NAV.find(window)
                    val navValue = nav?.groupValues?.get(2)?.let(::parseNumber)
                    val navDate = nav?.groupValues?.get(1)?.let(::parseDate) ?: asOf
                    snapshots += PositionSnapshot(
                        isin = currentIsin,
                        account = currentFolio,
                        platform = Platform.OTHER,
                        category = HoldingCategory.MF_FOLIO,
                        asOf = asOf,
                        units = units,
                        costValue = COST.find(window)?.groupValues?.get(1)?.let(::parseNumber),
                        price = navValue,
                    )
                    if (navValue != null && navValue > 0) prices += PricePoint(currentIsin, navDate, navValue)
                    isin = null
                }
            }
            i++
        }

        if (snapshots.isEmpty()) throw UnsupportedStatementException("No mutual fund folios found in this CAS")
        return ParsedStatement(StatementKind.CAMS_KFINTECH, asOf, instruments.values.toList(), snapshots, transactions, prices)
    }

    private fun transactionFrom(m: MatchResult, isin: String, folio: String): Transaction? {
        val g = m.groupValues
        val date = parseDate(g[1]) ?: return null
        val description = g[2]
        val amount = parseNumber(g[3]) ?: return null
        val units = parseNumber(g[4]) ?: return null
        val price = parseNumber(g[5]) ?: return null
        if (units == 0.0) return null
        val text = description.lowercase()
        val type = when {
            "switch" in text -> if (units < 0) TransactionType.SWITCH_OUT else TransactionType.SWITCH_IN
            units < 0 -> TransactionType.SELL
            "reinvest" in text -> TransactionType.DIVIDEND_REINVEST
            "bonus" in text -> TransactionType.BONUS
            else -> TransactionType.BUY
        }
        return Transaction(
            isin = isin,
            platform = Platform.OTHER,
            account = folio,
            date = date,
            type = type,
            units = abs(units),
            pricePerUnit = price,
            amount = abs(amount),
        )
    }

    private fun cleanSchemeName(raw: String): String =
        raw.trim()
            .replace(SCHEME_CODE_PREFIX, "")
            .replace(Regex("""\(\s*(formerly|erstwhile)[^)]*\)?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimEnd('-', ' ', '(')
            .trim()
}
