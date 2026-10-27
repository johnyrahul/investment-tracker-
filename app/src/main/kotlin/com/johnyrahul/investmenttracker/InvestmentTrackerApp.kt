package com.johnyrahul.investmenttracker

import android.app.Application
import com.johnyrahul.investmenttracker.core.data.db.PortfolioDatabase
import com.johnyrahul.investmenttracker.core.data.repository.PortfolioRepository
import com.johnyrahul.investmenttracker.core.data.repository.RoomPortfolioRepository
import com.johnyrahul.investmenttracker.core.data.repository.SettingsRepository
import com.johnyrahul.investmenttracker.core.sync.GmailSync
import com.johnyrahul.investmenttracker.core.sync.PdfStatementReader
import com.johnyrahul.investmenttracker.core.sync.PriceRefresher
import com.johnyrahul.investmenttracker.core.sync.StatementImporter
import com.johnyrahul.investmenttracker.core.sync.SyncGraph
import com.johnyrahul.investmenttracker.core.sync.SyncWorker

class InvestmentTrackerApp : Application(), SyncGraph {
    /** Manual dependency container; small enough that a DI framework isn't worth it yet. */
    private val database by lazy { PortfolioDatabase.create(this) }

    val portfolioRepository: PortfolioRepository by lazy { RoomPortfolioRepository(database) }
    val settingsRepository by lazy { SettingsRepository(database) }
    val statementImporter by lazy { StatementImporter(PdfStatementReader(this), portfolioRepository, settingsRepository) }
    override val gmailSync by lazy { GmailSync(this, statementImporter, settingsRepository) }
    override val priceRefresher by lazy { PriceRefresher(portfolioRepository, settingsRepository) }

    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedule(this)
    }
}
