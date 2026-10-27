package com.johnyrahul.investmenttracker.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.johnyrahul.investmenttracker.core.model.AssetType
import com.johnyrahul.investmenttracker.core.model.HoldingCategory
import com.johnyrahul.investmenttracker.core.model.Platform
import com.johnyrahul.investmenttracker.core.model.TransactionType
import java.time.LocalDate

@Entity(tableName = "instruments")
data class InstrumentEntity(
    @PrimaryKey val isin: String,
    val name: String,
    val type: AssetType,
    val amfiCode: String?,
    val symbol: String?,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = InstrumentEntity::class,
            parentColumns = ["isin"],
            childColumns = ["isin"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("isin"),
        // Makes re-importing the same or an overlapping statement idempotent.
        Index(value = ["isin", "account", "date", "type", "units", "amount"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isin: String,
    val platform: Platform,
    /** Folio number or demat account id. */
    val account: String,
    val date: LocalDate,
    val type: TransactionType,
    val units: Double,
    val pricePerUnit: Double,
    val amount: Double,
    /** Where this row came from, e.g. "CAMS / KFintech CAS 2026-10-31" or "manual". */
    val source: String,
)

/** Daily NAV / closing price history, from statements and the daily price refresh. */
@Entity(
    tableName = "prices",
    primaryKeys = ["isin", "date"],
    foreignKeys = [
        ForeignKey(
            entity = InstrumentEntity::class,
            parentColumns = ["isin"],
            childColumns = ["isin"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PriceEntity(
    val isin: String,
    val date: LocalDate,
    val price: Double,
)

/** Closing balance of one instrument in one account, as printed on a statement. */
@Entity(
    tableName = "snapshots",
    primaryKeys = ["isin", "account", "asOf"],
    foreignKeys = [
        ForeignKey(
            entity = InstrumentEntity::class,
            parentColumns = ["isin"],
            childColumns = ["isin"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SnapshotEntity(
    val isin: String,
    val account: String,
    val asOf: LocalDate,
    val platform: Platform,
    val category: HoldingCategory,
    val units: Double,
    val costValue: Double?,
    val price: Double?,
    val statementId: Long,
)

/** One imported CAS file. */
@Entity(tableName = "statements", indices = [Index(value = ["fileHash"], unique = true)])
data class StatementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val asOf: LocalDate,
    /** Comma-separated [HoldingCategory] names this statement is complete for. */
    val categories: String,
    val holdingsCount: Int,
    val transactionsCount: Int,
    val fileHash: String,
    /** "gmail", "file" or "share". */
    val origin: String,
    val importedAtMillis: Long,
)

/** Gmail messages already looked at, so the daily sync never downloads them twice. */
@Entity(tableName = "gmail_messages")
data class GmailMessageEntity(
    @PrimaryKey val messageId: String,
    /** "imported", "duplicate", "locked" (no password worked) or "unsupported". */
    val outcome: String,
    val checkedAtMillis: Long,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
