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

    @Query("SELECT * FROM instruments")
    suspend fun getAll(): List<InstrumentEntity>

    /** Keeps the first name seen; CAMS names are nicer than the upper-case depository ones. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(instruments: List<InstrumentEntity>)

    @Query("UPDATE instruments SET amfiCode = :amfiCode WHERE isin = :isin")
    suspend fun setAmfiCode(isin: String, amfiCode: String)

    @Query("UPDATE instruments SET symbol = :symbol WHERE isin = :isin")
    suspend fun setSymbol(isin: String, symbol: String)
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

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM snapshots")
    fun observeAll(): Flow<List<SnapshotEntity>>

    @Query("SELECT * FROM snapshots")
    suspend fun getAll(): List<SnapshotEntity>

    @Upsert
    suspend fun upsert(snapshots: List<SnapshotEntity>)
}

@Dao
interface StatementDao {
    @Query("SELECT * FROM statements ORDER BY asOf DESC, importedAtMillis DESC")
    fun observeAll(): Flow<List<StatementEntity>>

    @Query("SELECT * FROM statements")
    suspend fun getAll(): List<StatementEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM statements WHERE fileHash = :hash)")
    suspend fun exists(hash: String): Boolean

    @Insert
    suspend fun insert(statement: StatementEntity): Long
}

@Dao
interface GmailMessageDao {
    @Query("SELECT messageId FROM gmail_messages")
    suspend fun allIds(): List<String>

    @Upsert
    suspend fun upsert(message: GmailMessageEntity)

    /** Lets locked statements be retried after the user adds a password. */
    @Query("DELETE FROM gmail_messages WHERE outcome = 'locked'")
    suspend fun forgetLocked()
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(setting: SettingEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun delete(key: String)
}
