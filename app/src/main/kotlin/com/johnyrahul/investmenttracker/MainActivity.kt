package com.johnyrahul.investmenttracker

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.johnyrahul.investmenttracker.feature.importer.ImportRoute
import com.johnyrahul.investmenttracker.feature.importer.ImportViewModel
import com.johnyrahul.investmenttracker.feature.portfolio.PortfolioRoute
import com.johnyrahul.investmenttracker.feature.portfolio.PortfolioViewModel
import com.johnyrahul.investmenttracker.ui.InvestmentTrackerTheme

class MainActivity : ComponentActivity() {

    /** A CAS PDF shared to or opened with the app, waiting to be imported. */
    private var sharedPdf by mutableStateOf<Uri?>(null)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep financial data out of screenshots and the recent-apps preview.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        if (savedInstanceState == null) sharedPdf = pdfFrom(intent)

        val app = application as InvestmentTrackerApp
        setContent {
            InvestmentTrackerTheme {
                var showImport by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = showImport) { showImport = false }

                val importViewModel: ImportViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            ImportViewModel(
                                appContext = app,
                                importer = app.statementImporter,
                                settings = app.settingsRepository,
                                portfolio = app.portfolioRepository,
                                gmailSync = app.gmailSync,
                                priceRefresher = app.priceRefresher,
                            )
                        }
                    },
                )
                LaunchedEffect(sharedPdf) {
                    sharedPdf?.let {
                        showImport = true
                        importViewModel.importUri(it, origin = "share")
                        sharedPdf = null
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(if (showImport) "Import" else stringResource(R.string.app_name)) },
                            navigationIcon = {
                                if (showImport) TextButton(onClick = { showImport = false }) { Text("Back") }
                            },
                            actions = {
                                if (!showImport) TextButton(onClick = { showImport = true }) { Text("Import") }
                            },
                        )
                    },
                ) { padding ->
                    if (showImport) {
                        ImportRoute(importViewModel, Modifier.padding(padding))
                    } else {
                        val portfolioViewModel: PortfolioViewModel = viewModel(
                            factory = viewModelFactory { initializer { PortfolioViewModel(app.portfolioRepository) } },
                        )
                        PortfolioRoute(portfolioViewModel, Modifier.padding(padding))
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pdfFrom(intent)?.let { sharedPdf = it }
    }

    private fun pdfFrom(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        else -> null
    }
}
