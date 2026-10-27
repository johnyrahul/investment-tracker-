package com.johnyrahul.investmenttracker.core.data.repository

import androidx.room.withTransaction
import com.johnyrahul.investmenttracker.core.data.db.InstrumentEntity
import com.johnyrahul.investmenttracker.core.data.db.PortfolioDatabase
import com.johnyrahul.investmenttracker.core.data.db.TransactionEntity
import com.johnyrahul.investmenttracker.core.model.Holding
import com.johnyrahul.investmenttracker.core.model.HoldingsCalculator
import com.johnyrahul.investmenttracker.core.model.Instrument
import com.johnyrahul.investmenttracker.core.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface PortfolioRepository {
    fun observeHoldings(): Flow<List<Holding>>

    /** Entry point for importers (CAS PDF, broker exports). Duplicate rows are ignored. */
    suspend fun import(instruments: List<Instrument>, transactions: List<Transaction>, source: String)
}

class RoomPortfolioRepository(private val db: PortfolioDatabase) : PortfolioRepository {

    override fun observeHoldings(): Flow<List<Holding>> = combine(
        db.instrumentDao().observeAll(),
        db.transactionDao().observeAll(),
        db.priceDao().observeLatest(),
    ) { instruments, transactions, prices ->
        HoldingsCalculator.compute(
            instruments = instruments.associate { it.isin to it.toModel() },
            transactions = transactions.map { it.toModel() },
            latestPrices = prices.associate { it.isin to it.price },
        )
    }

    override suspend fun import(instruments: List<Instrument>, transactions: List<Transaction>, source: String) {
        db.withTransaction {
            db.instrumentDao().upsert(instruments.map { it.toEntity() })
            db.transactionDao().insertAll(transactions.map { it.toEntity(source) })
        }
    }
}

private fun InstrumentEntity.toModel() = Instrument(isin, name, type, amfiCode, symbol)

private fun Instrument.toEntity() = InstrumentEntity(isin, name, type, amfiCode, symbol)

private fun TransactionEntity.toModel() =
    Transaction(id, isin, platform, date, type, units, pricePerUnit, amount)

private fun Transaction.toEntity(source: String) =
    TransactionEntity(0, isin, platform, date, type, units, pricePerUnit, amount, source)
