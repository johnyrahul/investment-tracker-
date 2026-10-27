package com.johnyrahul.investmenttracker.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface InstrumentDao {
    @Query("SELECT * FROM instruments")
    fun observeAll(): Flow<List<InstrumentEntity>>

    @Upsert
    suspend fun upsert(instruments: List<InstrumentEntity>)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>
}

data class LatestPrice(val isin: String, val price: Double)

@Dao
interface PriceDao {
    @Query(
        """
        SELECT p.isin, p.price FROM prices p
        JOIN (SELECT isin, MAX(date) AS maxDate FROM prices GROUP BY isin) latest
          ON p.isin = latest.isin AND p.date = latest.maxDate
        """,
    )
    fun observeLatest(): Flow<List<LatestPrice>>

    @Upsert
    suspend fun upsert(prices: List<PriceEntity>)
}
