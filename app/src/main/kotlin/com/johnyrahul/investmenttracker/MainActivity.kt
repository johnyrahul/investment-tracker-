package com.johnyrahul.investmenttracker

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.johnyrahul.investmenttracker.feature.portfolio.PortfolioRoute
import com.johnyrahul.investmenttracker.feature.portfolio.PortfolioViewModel
import com.johnyrahul.investmenttracker.ui.InvestmentTrackerTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep financial data out of screenshots and the recent-apps preview.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        val repository = (application as InvestmentTrackerApp).portfolioRepository
        setContent {
            InvestmentTrackerTheme {
                Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
                    val portfolioViewModel: PortfolioViewModel = viewModel(
                        factory = viewModelFactory { initializer { PortfolioViewModel(repository) } },
                    )
                    PortfolioRoute(portfolioViewModel, Modifier.padding(padding))
                }
            }
        }
    }
}
