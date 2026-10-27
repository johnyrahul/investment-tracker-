package com.johnyrahul.investmenttracker.feature.portfolio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.johnyrahul.investmenttracker.core.model.AssetType
import com.johnyrahul.investmenttracker.core.model.Holding
import com.johnyrahul.investmenttracker.core.model.Platform
import java.text.NumberFormat
import java.util.Locale

private val inr: NumberFormat = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build())

@Composable
fun PortfolioRoute(viewModel: PortfolioViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PortfolioScreen(state, modifier)
}

@Composable
fun PortfolioScreen(state: PortfolioUiState, modifier: Modifier = Modifier) {
    when {
        state.loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.holdings.isEmpty() -> Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "No holdings yet.\nTap Import to add your CAS statement.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        else -> LazyColumn(
            modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Summary(state) }
            items(state.holdings, key = { "${it.instrument.isin}-${it.account}" }) { HoldingRow(it) }
        }
    }
}

@Composable
private fun Summary(state: PortfolioUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Current value", style = MaterialTheme.typography.labelMedium)
            Text(inr.format(state.totalValue), style = MaterialTheme.typography.headlineMedium)
            Text("Invested ${inr.format(state.totalInvested)}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Gain ${inr.format(state.totalGain)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.totalGain >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            val unknownCost = state.holdings.count { it.investedAmount == null }
            if (unknownCost > 0) {
                Text(
                    "Purchase cost unknown for $unknownCost holdings (depository statements don't include it)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun HoldingRow(holding: Holding) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(holding.instrument.name, style = MaterialTheme.typography.titleSmall)
                Text(holding.subtitle(), style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                (holding.currentValue ?: holding.investedAmount)?.let { Text(inr.format(it)) }
                holding.gain?.let {
                    Text(
                        inr.format(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun Holding.subtitle(): String = buildList {
    if (platform != Platform.OTHER) add(platform.displayName)
    if (instrument.type == AssetType.MUTUAL_FUND && account.any(Char::isDigit) && account.length < 16) {
        add("Folio $account")
    }
    add("${units.formatUnits()} ${if (instrument.type == AssetType.STOCK) "shares" else "units"}")
}.joinToString(" · ")

private fun Double.formatUnits(): String =
    if (this % 1.0 == 0.0) toLong().toString() else "%.3f".format(this)
