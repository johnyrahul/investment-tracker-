package com.johnyrahul.investmenttracker.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.johnyrahul.investmenttracker.core.model.AssetType
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
        // Makes re-importing the same statement idempotent.
        Index(value = ["isin", "platform", "date", "type", "units", "amount"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isin: String,
    val platform: Platform,
    val date: LocalDate,
    val type: TransactionType,
    val units: Double,
    val pricePerUnit: Double,
    val amount: Double,
    /** Where this row came from, e.g. "CAS:2026-10" or "manual". */
    val source: String,
)

/** Daily NAV / closing price history, filled by the price refresh job in a later step. */
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
