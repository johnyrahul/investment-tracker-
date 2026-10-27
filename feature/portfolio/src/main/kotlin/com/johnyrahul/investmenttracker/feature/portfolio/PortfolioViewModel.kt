package com.johnyrahul.investmenttracker.feature.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johnyrahul.investmenttracker.core.data.repository.PortfolioRepository
import com.johnyrahul.investmenttracker.core.model.Holding
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PortfolioUiState(
    val loading: Boolean = true,
    val holdings: List<Holding> = emptyList(),
) {
    val totalInvested: Double get() = holdings.sumOf { it.investedAmount }
    val totalValue: Double get() = holdings.sumOf { it.currentValue ?: it.investedAmount }
}

class PortfolioViewModel(repository: PortfolioRepository) : ViewModel() {
    val state: StateFlow<PortfolioUiState> = repository.observeHoldings()
        .map { PortfolioUiState(loading = false, holdings = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PortfolioUiState())
}
