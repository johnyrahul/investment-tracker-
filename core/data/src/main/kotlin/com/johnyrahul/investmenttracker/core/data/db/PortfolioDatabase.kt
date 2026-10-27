package com.johnyrahul.investmenttracker.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.johnyrahul.investmenttracker.core.data.security.DatabaseKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [InstrumentEntity::class, TransactionEntity::class, PriceEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PortfolioDatabase : RoomDatabase() {
    abstract fun instrumentDao(): InstrumentDao
    abstract fun transactionDao(): TransactionDao
    abstract fun priceDao(): PriceDao

    companion object {
        private const val NAME = "portfolio.db"

        /**
         * Opens the SQLCipher-encrypted database. The passphrase is random per install and
         * stored wrapped by a hardware-backed Android Keystore key, so the file is unreadable
         * off-device.
         */
        fun create(context: Context): PortfolioDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabaseKeyManager(context).getOrCreatePassphrase()
            return Room.databaseBuilder(context, PortfolioDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }
    }
}
