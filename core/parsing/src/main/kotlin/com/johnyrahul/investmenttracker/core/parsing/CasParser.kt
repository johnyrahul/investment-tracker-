package com.johnyrahul.investmenttracker.core.parsing

/** Entry point: turns the text of an unlocked CAS PDF into holdings, transactions and prices. */
object CasParser {

    fun parse(text: String): ParsedStatement {
        val lines = text.lines().map { it.replace(' ', ' ').trimEnd() }.filter { it.isNotBlank() }
        return when {
            RtaCasParser.looksLikeRtaCas(text) -> RtaCasParser.parse(lines)
            DepositoryCasParser.looksLikeDepositoryCas(text) -> DepositoryCasParser.parse(lines)
            else -> throw UnsupportedStatementException(
                "This doesn't look like a CAS from CAMS, KFintech, NSDL or CDSL",
            )
        }
    }
}
