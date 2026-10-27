package com.johnyrahul.investmenttracker.core.parsing

import com.johnyrahul.investmenttracker.core.model.AssetType
import com.johnyrahul.investmenttracker.core.model.HoldingCategory
import com.johnyrahul.investmenttracker.core.model.Instrument
import com.johnyrahul.investmenttracker.core.model.Platform
import com.johnyrahul.investmenttracker.core.model.PositionSnapshot
import com.johnyrahul.investmenttracker.core.model.PricePoint
import java.time.LocalDate
import kotlin.math.abs

/**
 * Parses the monthly Consolidated Account Statement from NSDL or CDSL, which lists holdings in
 * every demat account plus every mutual fund folio linked to the PAN.
 *
 * The two depositories lay their tables out differently and change them now and then, so rows
 * are read by meaning rather than by column position: a holding row is a line with an ISIN and
 * three numbers where units x price = value.
 */
internal object DepositoryCasParser {

    private val BO_ID = Regex("""BO\s*ID\s*:?\s*(\d{16})""", RegexOption.IGNORE_CASE)
    private val DP_ID = Regex("""DP\s*ID\s*:?\s*(IN\d{6}|\d{8})""", RegexOption.IGNORE_CASE)
    private val CLIENT_ID = Regex("""Client\s*ID\s*:?\s*(\d{8})""", RegexOption.IGNORE_CASE)
    private val FOLIO_TOKEN = Regex("""^\d{5,}(?:/\d+)?$""")
    private val UCC_TOKEN = Regex("""^(?=.*\d)(?=.*[A-Z])[A-Z0-9]{6,}$""")
    private val AS_ON = Regex("""as\s+on\s+""", RegexOption.IGNORE_CASE)

    private enum class Section { NONE, DEMAT, MF_FOLIO }

    private data class AccountHeader(val line: Int, val account: String, val platform: Platform)

    fun looksLikeDepositoryCas(text: String): Boolean =
        ISIN_REGEX.containsMatchIn(text) &&
            (text.contains("NSDL") || text.contains("CDSL") || text.contains("Depository", ignoreCase = true))

    fun parse(lines: List<String>): ParsedStatement {
        val head = lines.take(80).joinToString("\n")
        val kind = if (head.contains("NSDL") || head.contains("National Securities Depository", ignoreCase = true)) {
            StatementKind.NSDL
        } else {
            StatementKind.CDSL
        }
        val asOf = findPeriodEnd(lines.take(80)) ?: findAsOn(lines)
            ?: throw UnsupportedStatementException("Couldn't find the statement date in this CAS")

        val headers = accountHeaders(lines)
        // Holdings tables are normally introduced by an account header or a section heading; if
        // neither was recognised, read every ISIN row as a demat holding rather than find nothing.
        val result = collect(lines, headers, asOf, Section.NONE).takeIf { it.second.isNotEmpty() }
            ?: collect(lines, headers, asOf, Section.DEMAT)
        val (instruments, snapshots, prices) = result
        if (snapshots.isEmpty()) throw UnsupportedStatementException("No holdings found in this ${kind.displayName}")
        return ParsedStatement(kind, asOf, instruments, snapshots, emptyList(), prices)
    }

    private fun collect(
        lines: List<String>,
        headers: List<AccountHeader>,
        asOf: LocalDate,
        initialSection: Section,
    ): Triple<List<Instrument>, List<PositionSnapshot>, List<PricePoint>> {
        val instruments = LinkedHashMap<String, Instrument>()
        val snapshots = LinkedHashMap<Pair<String, String>, PositionSnapshot>()
        val prices = LinkedHashMap<String, PricePoint>()

        var section = initialSection
        var account: AccountHeader? = null
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            headers.lastOrNull { it.line == i }?.let {
                account = it
                section = Section.DEMAT
            }
            val isinMatch = ISIN_REGEX.find(line)
            if (isinMatch == null) {
                section = nextSection(line, section)
                i++
                continue
            }
            if (section == Section.NONE) {
                i++
                continue
            }

            // Try the row on its own first, then with up to two continuation lines for wrapped cells.
            var parsed: Row? = null
            var used = 0
            for (extra in 0..2) {
                val continuation = (1..extra).map { lines.getOrNull(i + it).orEmpty() }
                if (continuation.any { ISIN_REGEX.containsMatchIn(it) }) break
                parsed = parseRow(line, continuation, section)
                if (parsed != null) {
                    used = extra
                    break
                }
            }
            var row = parsed
            if (row != null) {
                // Name cells that wrap leave short digit-free lines below the row.
                while (true) {
                    val next = lines.getOrNull(i + used + 1)?.trim() ?: break
                    if (!isNameContinuation(next)) break
                    row = row!!.copy(name = "${row.name} $next".trim())
                    used++
                }
            }
            if (row != null) {
                val isin = isinMatch.value
                // A row without a folio number in the MF section is demat-held units.
                val folio = row.folio.takeIf { section == Section.MF_FOLIO }
                val acct = folio ?: account?.account ?: "DEMAT"
                val category = if (folio != null) HoldingCategory.MF_FOLIO else HoldingCategory.DEMAT
                val platform = if (folio == null) account?.platform ?: Platform.OTHER else Platform.OTHER
                instruments.putIfAbsent(isin, Instrument(isin, row.name.ifBlank { isin }, AssetType.fromIsin(isin)))
                snapshots.putIfAbsent(
                    isin to acct,
                    PositionSnapshot(isin, acct, platform, category, asOf, row.units, row.cost, row.price),
                )
                prices.putIfAbsent(isin, PricePoint(isin, asOf, row.price))
                i += used
            }
            i++
        }

        return Triple(instruments.values.toList(), snapshots.values.toList(), prices.values.toList())
    }

    private fun nextSection(line: String, current: Section): Section {
        val lower = line.lowercase()
        return when {
            "transaction" in lower -> Section.NONE
            "demat" in lower || "holding statement" in lower || lower.startsWith("equities") -> Section.DEMAT
            "mutual fund" in lower && ("folio" in lower || "held" in lower) -> Section.MF_FOLIO
            else -> current
        }
    }

    private fun isNameContinuation(line: String): Boolean =
        line.none(Char::isDigit) &&
            line.split(Regex("""\s+""")).size <= 5 &&
            !line.contains("total", ignoreCase = true) &&
            Section.entries.all { nextSection(line, it) == it }

    private data class Row(val name: String, val units: Double, val price: Double, val cost: Double?, val folio: String?)

    private fun parseRow(line: String, continuation: List<String>, section: Section): Row? {
        val isin = ISIN_REGEX.find(line)!!.value
        val text = (listOf(line) + continuation).joinToString(" ")
        var body = text.replace(isin, " ")
        for ((range, _) in findDates(body).reversed()) body = body.replaceRange(range, " ")

        val rawTokens = body.split(Regex("""\s+""")).filter { it.isNotBlank() }
        var folio: String? = null
        if (section == Section.MF_FOLIO) folio = rawTokens.firstOrNull { FOLIO_TOKEN.matches(it) }

        val numbers = NUMBER_REGEX.findAll(body)
            .filter { it.value != folio }
            .mapNotNull { parseNumber(it.value) }
            .toList()
        val triple = bestTriple(numbers) ?: return null
        val (u, p, v) = triple
        val units = numbers[u]
        val value = numbers[v]

        val name = (listOf(line) + continuation.filter { !NUMBER_REGEX.containsMatchIn(it) })
            .joinToString(" ")
            .replace(isin, " ")
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() && it != folio && it != "--" && !NUMBER_REGEX.matches(it) && !UCC_TOKEN.matches(it) }
            .joinToString(" ")
            .substringBefore('#')
            .trim()

        return Row(name, units, numbers[p], costFor(numbers, triple, value), folio)
    }

    /**
     * Picks indices (units, price, value) with units x price = value. When several match (NSDL lists
     * units x average cost = total cost before units x NAV = value) the later columns are the current value.
     */
    private fun bestTriple(n: List<Double>): Triple<Int, Int, Int>? {
        var best: Triple<Int, Int, Int>? = null
        for (k in n.indices.reversed()) {
            if (n[k] <= 0) continue
            for (j in (0 until k).reversed()) {
                if (n[j] <= 0) continue
                for (i in (0 until j).reversed()) {
                    if (n[i] <= 0) continue
                    if (n[i] * n[j] matches n[k]) {
                        best = Triple(i, j, k)
                        break
                    }
                }
                if (best != null) break
            }
            if (best != null) break
        }
        return best
    }

    private fun costFor(n: List<Double>, triple: Triple<Int, Int, Int>, value: Double): Double? {
        val (u, p, v) = triple
        val tolerance = maxOf(0.05, 0.002 * value)
        // An earlier units x average cost = total cost triple (NSDL).
        for (k in (0 until v).reversed()) {
            if (k == u || k == p || n[k] <= 0) continue
            for (j in 0 until k) {
                if (j == u || j == p) continue
                if (n[u] * n[j] matches n[k]) return n[k]
            }
        }
        // Otherwise cost + unrealised gain = value (CDSL "amount invested" and "unrealised P/L").
        for (c in n.indices) {
            if (c == u || c == p || c == v || n[c] <= 0) continue
            for (g in n.indices) {
                if (g == u || g == p || g == v || g == c) continue
                if (abs(n[c] + n[g] - value) <= tolerance) return n[c]
            }
        }
        return null
    }

    /** Statements round values to paise and NAVs to 4 decimals, so allow a small relative error. */
    private infix fun Double.matches(expected: Double) = abs(this - expected) <= maxOf(0.05, 0.003 * expected)

    private fun accountHeaders(lines: List<String>): List<AccountHeader> {
        val headers = mutableListOf<AccountHeader>()
        for ((index, line) in lines.withIndex()) {
            if (ISIN_REGEX.containsMatchIn(line)) continue
            val account = BO_ID.find(line)?.groupValues?.get(1)
                ?: DP_ID.find(line)?.let { dp -> CLIENT_ID.find(line)?.let { dp.groupValues[1] + it.groupValues[1] } }
                ?: continue
            val nearby = lines.subList(maxOf(0, index - 3), minOf(lines.size, index + 4)).joinToString(" ")
            headers += AccountHeader(index, account, Platform.fromBrokerName(nearby))
        }
        return headers
    }

    private fun findAsOn(lines: List<String>): LocalDate? =
        lines.asSequence()
            .mapNotNull { line -> AS_ON.find(line)?.let { parseDate(line.substring(it.range.last + 1)) } }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
}
