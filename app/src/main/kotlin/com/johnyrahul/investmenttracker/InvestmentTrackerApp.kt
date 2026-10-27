package com.johnyrahul.investmenttracker

import android.app.Application
import com.johnyrahul.investmenttracker.core.data.db.PortfolioDatabase
import com.johnyrahul.investmenttracker.core.data.repository.PortfolioRepository
import com.johnyrahul.investmenttracker.core.data.repository.RoomPortfolioRepository

class InvestmentTrackerApp : Application() {
    /** Manual dependency container; small enough that a DI framework isn't worth it yet. */
    val portfolioRepository: PortfolioRepository by lazy {
        RoomPortfolioRepository(PortfolioDatabase.create(this))
    }
}
