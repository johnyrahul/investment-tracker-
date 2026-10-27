package com.johnyrahul.investmenttracker.core.data.repository

import androidx.room.withTransaction
import com.johnyrahul.investmenttracker.core.data.db.InstrumentEntity
import com.johnyrahul.investmenttracker.core.data.db.PortfolioDatabase
import com.johnyrahul.investmenttracker.core.data.db.PriceEntity
import com.johnyrahul.investmenttracker.core.data.db.SnapshotEntity
import com.johnyrahul.investmenttracker.core.data.db.StatementEntity
import com.johnyrahul.investmenttracker.core.data.db.TransactionEntity
import com.johnyrahul.investmenttracker.core.model.Holding
import com.johnyrahul.investmenttracker.core.model.HoldingCategory
import com.johnyrahul.investmenttracker.core.model.Instrument
import com.johnyrahul.investmenttracker.core.model.PortfolioResolver
import com.johnyrahul.investmenttracker.core.model.PositionSnapshot
import com.johnyrahul.investmenttracker.core.model.PricePoint
import com.johnyrahul.investmenttracker.core.model.Transaction
import com.johnyrahul.investmenttracker.core.parsing.ParsedStatement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** An imported CAS, as shown in the import history. */
data class StatementRecord(
    val kind: String,
    val asOf: LocalDate,
    val holdingsCount: Int,
    val transactionsCount: Int,
    val origin: String,
    val importedAtMillis: Long,
)

sealed interface ImportOutcome {
    data class Imported(val statement: StatementRecord) : ImportOutcome
    data object AlreadyImported : ImportOutcome
}

interface PortfolioRepository {
    fun observeHoldings(): Flow<List<Holding>>

    fun observeStatements(): Flow<List<StatementRecord>>

    suspend fun isImported(fileHash: String): Boolean

    /** Saves a parsed CAS. Re-importing the same file, or overlapping transactions, is a no-op. */
    suspend fun importStatement(statement: ParsedStatement, fileHash: String, origin: String): ImportOutcome

    /** Instruments that appear in any statement snapshot, for the price refresh. */
    suspend fun trackedInstruments(): List<Instrument>

    suspend fun savePrices(prices: List<PricePoint>, amfiCodes: Map<String, String>, symbols: Map<String, String>)
}

class RoomPortfolioRepository(private val db: PortfolioDatabase) : PortfolioRepository {

    override fun observeHoldings(): Flow<List<Holding>> = combine(
        db.instrumentDao().observeAll(),
        db.snapshotDao().observeAll(),
        db.statementDao().observeAll(),
        db.transactionDao().observeAll(),
        db.priceDao().observeLatest(),
    ) { instruments, snapshots, statements, transactions, prices ->
        PortfolioResolver.resolve(
            instruments = instruments.associate { it.isin to it.toModel() },
            snapshots = snapshots.map { it.toModel() },
            latestStatementByCategory = latestByCategory(statements),
            transactions = transactions.map { it.toModel() },
            latestPrices = prices.associate { it.isin to it.price },
        )
    }

    override fun observeStatements(): Flow<List<StatementRecord>> =
        db.statementDao().observeAll().map { rows -> rows.map { it.toRecord() } }

    override suspend fun isImported(fileHash: String): Boolean = db.statementDao().exists(fileHash)

    override suspend fun importStatement(statement: ParsedStatement, fileHash: String, origin: String): ImportOutcome =
        db.withTransaction {
            if (db.statementDao().exists(fileHash)) return@withTransaction ImportOutcome.AlreadyImported
            val entity = StatementEntity(
                kind = statement.kind.displayName,
                asOf = statement.asOf,
                categories = statement.categories.joinToString(",") { it.name },
                holdingsCount = statement.snapshots.count { it.units > 0 },
                transactionsCount = statement.transactions.size,
                fileHash = fileHash,
                origin = origin,
                importedAtMillis = System.currentTimeMillis(),
            )
            val id = db.statementDao().insert(entity)
            val source = "${statement.kind.displayName} ${statement.asOf}"
            db.instrumentDao().insertIfAbsent(statement.instruments.map { it.toEntity() })
            db.snapshotDao().upsert(statement.snapshots.map { it.toEntity(id) })
            db.transactionDao().insertAll(statement.transactions.map { it.toEntity(source) })
            db.priceDao().upsert(statement.prices.map { PriceEntity(it.isin, it.date, it.price) })
            ImportOutcome.Imported(entity.toRecord())
        }

    override suspend fun trackedInstruments(): List<Instrument> {
        val held = db.snapshotDao().getAll().mapTo(HashSet()) { it.isin }
        return db.instrumentDao().getAll().filter { it.isin in held }.map { it.toModel() }
    }

    override suspend fun savePrices(prices: List<PricePoint>, amfiCodes: Map<String, String>, symbols: Map<String, String>) {
        db.withTransaction {
            db.priceDao().upsert(prices.map { PriceEntity(it.isin, it.date, it.price) })
            amfiCodes.forEach { (isin, code) -> db.instrumentDao().setAmfiCode(isin, code) }
            symbols.forEach { (isin, symbol) -> db.instrumentDao().setSymbol(isin, symbol) }
        }
    }

    private fun latestByCategory(statements: List<StatementEntity>): Map<HoldingCategory, LocalDate> {
        val latest = HashMap<HoldingCategory, LocalDate>()
        for (s in statements) {
            for (name in s.categories.split(',').filter { it.isNotBlank() }) {
                val category = runCatching { HoldingCategory.valueOf(name) }.getOrNull() ?: continue
                if (latest[category]?.let { it < s.asOf } != false) latest[category] = s.asOf
            }
        }
        return latest
    }
}

private fun InstrumentEntity.toModel() = Instrument(isin, name, type, amfiCode, symbol)

private fun Instrument.toEntity() = InstrumentEntity(isin, name, type, amfiCode, symbol)

private fun TransactionEntity.toModel() =
    Transaction(id, isin, platform, account, date, type, units, pricePerUnit, amount)

private fun Transaction.toEntity(source: String) =
    TransactionEntity(0, isin, platform, account, date, type, units, pricePerUnit, amount, source)

private fun SnapshotEntity.toModel() =
    PositionSnapshot(isin, account, platform, category, asOf, units, costValue, price)

private fun PositionSnapshot.toEntity(statementId: Long) =
    SnapshotEntity(isin, account, asOf, platform, category, units, costValue, price, statementId)

private fun StatementEntity.toRecord() =
    StatementRecord(kind, asOf, holdingsCount, transactionsCount, origin, importedAtMillis)
