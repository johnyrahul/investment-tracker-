package com.johnyrahul.investmenttracker.core.parsing

import com.johnyrahul.investmenttracker.core.model.HoldingCategory
import com.johnyrahul.investmenttracker.core.model.Instrument
import com.johnyrahul.investmenttracker.core.model.PositionSnapshot
import com.johnyrahul.investmenttracker.core.model.PricePoint
import com.johnyrahul.investmenttracker.core.model.Transaction
import java.time.LocalDate

enum class StatementKind(val displayName: String) {
    CAMS_KFINTECH("CAMS / KFintech CAS"),
    NSDL("NSDL CAS"),
    CDSL("CDSL CAS"),
}

data class ParsedStatement(
    val kind: StatementKind,
    /** End of the statement period; all [snapshots] are as of this date. */
    val asOf: LocalDate,
    val instruments: List<Instrument>,
    val snapshots: List<PositionSnapshot>,
    val transactions: List<Transaction>,
    /** NAVs / market prices printed on the statement. */
    val prices: List<PricePoint>,
) {
    /** Categories this statement is a complete record of (see PortfolioResolver). */
    val categories: Set<HoldingCategory> get() = snapshots.mapTo(LinkedHashSet()) { it.category }
}

class UnsupportedStatementException(message: String) : Exception(message)
