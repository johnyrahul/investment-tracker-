package com.johnyrahul.investmenttracker.feature.importer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johnyrahul.investmenttracker.core.data.repository.PortfolioRepository
import com.johnyrahul.investmenttracker.core.data.repository.SettingsRepository
import com.johnyrahul.investmenttracker.core.data.repository.StatementRecord
import com.johnyrahul.investmenttracker.core.sync.GmailAuth
import com.johnyrahul.investmenttracker.core.sync.GmailSync
import com.johnyrahul.investmenttracker.core.sync.ImportResult
import com.johnyrahul.investmenttracker.core.sync.PriceRefresher
import com.johnyrahul.investmenttracker.core.sync.SigningInfo
import com.johnyrahul.investmenttracker.core.sync.StatementImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

data class ImportUiState(
    val pan: String? = null,
    val extraPasswordCount: Int = 0,
    val gmailEnabled: Boolean = false,
    val gmailStatus: String? = null,
    val pricesStatus: String? = null,
    val statements: List<StatementRecord> = emptyList(),
    val busy: String? = null,
    val message: String? = null,
    /** A PDF that none of the saved passwords opened; the UI asks for its password. */
    val needsPassword: Boolean = false,
    /** Google's consent screen to launch for Gmail access. */
    val gmailConsent: PendingIntent? = null,
    val packageName: String = "",
    val signingSha1: String? = null,
)

private data class Transient(
    val busy: String? = null,
    val message: String? = null,
    val pendingPdf: Pair<ByteArray, String>? = null,
    val gmailConsent: PendingIntent? = null,
)

class ImportViewModel(
    private val appContext: Context,
    private val importer: StatementImporter,
    private val settings: SettingsRepository,
    portfolio: PortfolioRepository,
    private val gmailSync: GmailSync,
    private val priceRefresher: PriceRefresher,
) : ViewModel() {

    private val transient = MutableStateFlow(Transient())
    private val sha1 = SigningInfo.sha1(appContext)

    val state: StateFlow<ImportUiState> = combine(
        settings.observe(),
        portfolio.observeStatements(),
        transient,
    ) { values, statements, t ->
        ImportUiState(
            pan = values[SettingsRepository.PAN],
            extraPasswordCount = values[SettingsRepository.EXTRA_PASSWORDS].orEmpty().lines().count { it.isNotBlank() },
            gmailEnabled = values[SettingsRepository.GMAIL_ENABLED] == "true",
            gmailStatus = values[SettingsRepository.GMAIL_STATUS],
            pricesStatus = values[SettingsRepository.PRICES_STATUS],
            statements = statements,
            busy = t.busy,
            message = t.message,
            needsPassword = t.pendingPdf != null,
            gmailConsent = t.gmailConsent,
            packageName = appContext.packageName,
            signingSha1 = sha1,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportUiState())

    fun savePan(pan: String): Boolean {
        val normalized = pan.trim().uppercase()
        if (!PAN_REGEX.matches(normalized)) {
            transient.update { it.copy(message = "That doesn't look like a PAN (e.g. ABCDE1234F)") }
            return false
        }
        viewModelScope.launch {
            settings.setPan(normalized)
            transient.update { it.copy(message = "PAN saved") }
        }
        return true
    }

    fun addPassword(password: String) {
        if (password.isBlank()) return
        viewModelScope.launch {
            settings.addStatementPassword(password.trim())
            transient.update { it.copy(message = "Password saved") }
        }
    }

    fun importUri(uri: Uri, origin: String) {
        viewModelScope.launch {
            val bytes = runCatching {
                withContext(Dispatchers.IO) { appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
            }.getOrNull()
            if (bytes == null) {
                transient.update { it.copy(message = "Couldn't read that file") }
                return@launch
            }
            runImport(bytes, origin, password = null)
        }
    }

    fun submitPassword(password: String) {
        val (bytes, origin) = transient.value.pendingPdf ?: return
        viewModelScope.launch { runImport(bytes, origin, password) }
    }

    fun cancelPassword() = transient.update { it.copy(pendingPdf = null) }

    fun dismissMessage() = transient.update { it.copy(message = null) }

    private suspend fun runImport(bytes: ByteArray, origin: String, password: String?) {
        transient.update { it.copy(busy = "Reading statement…", pendingPdf = null) }
        val result = importer.import(bytes, origin, password)
        transient.update {
            when (result) {
                is ImportResult.Imported -> it.copy(
                    busy = null,
                    message = "Imported ${result.statement.kind} as of ${result.statement.asOf.format(DATE)}: " +
                        "${result.statement.holdingsCount} holdings",
                )
                ImportResult.AlreadyImported -> it.copy(busy = null, message = "This statement was already imported")
                ImportResult.NeedsPassword -> it.copy(
                    busy = null,
                    pendingPdf = bytes to origin,
                    message = if (password != null) "That password didn't work" else null,
                )
                is ImportResult.Unsupported -> it.copy(busy = null, message = result.reason)
                is ImportResult.Failed -> it.copy(busy = null, message = "Import failed: ${result.reason}")
            }
        }
        if (result is ImportResult.Imported) refreshPricesQuietly()
    }

    fun connectGmail() {
        viewModelScope.launch {
            transient.update { it.copy(busy = "Connecting to Gmail…") }
            try {
                when (val auth = GmailAuth.authorize(appContext)) {
                    is GmailAuth.Result.NeedsConsent -> transient.update { it.copy(busy = null, gmailConsent = auth.pendingIntent) }
                    is GmailAuth.Result.Token -> onGmailAuthorized()
                }
            } catch (e: Exception) {
                transient.update { it.copy(busy = null, message = gmailError(e)) }
            }
        }
    }

    /** Called with the result of the consent screen launched for [ImportUiState.gmailConsent]. */
    fun onGmailConsentResult(data: Intent?) {
        transient.update { it.copy(gmailConsent = null) }
        viewModelScope.launch {
            try {
                when (GmailAuth.resultFromIntent(appContext, data)) {
                    is GmailAuth.Result.Token -> onGmailAuthorized()
                    is GmailAuth.Result.NeedsConsent -> transient.update { it.copy(message = "Gmail access wasn't granted") }
                }
            } catch (e: Exception) {
                transient.update { it.copy(busy = null, message = gmailError(e)) }
            }
        }
    }

    fun disconnectGmail() {
        viewModelScope.launch {
            settings.put(SettingsRepository.GMAIL_ENABLED, null)
            settings.put(SettingsRepository.GMAIL_STATUS, null)
            transient.update {
                it.copy(message = "Gmail disconnected. To revoke access fully, remove the app at myaccount.google.com/permissions")
            }
        }
    }

    private suspend fun onGmailAuthorized() {
        settings.put(SettingsRepository.GMAIL_ENABLED, "true")
        checkGmailNow()
    }

    fun checkGmailNow() {
        viewModelScope.launch {
            transient.update { it.copy(busy = "Checking Gmail for statements…") }
            val result = gmailSync.sync()
            transient.update { it.copy(busy = null) }
            when (result) {
                is GmailSync.Result.NeedsConsent -> connectGmail()
                is GmailSync.Result.Done -> if (result.imported > 0) refreshPricesQuietly()
                else -> Unit
            }
        }
    }

    fun refreshPrices() {
        viewModelScope.launch {
            transient.update { it.copy(busy = "Updating prices…") }
            priceRefresher.refresh()
            transient.update { it.copy(busy = null) }
        }
    }

    private fun refreshPricesQuietly() {
        viewModelScope.launch { priceRefresher.refresh() }
    }

    private fun gmailError(e: Exception): String {
        val text = e.message.orEmpty()
        // Play services reports an unregistered package / SHA-1 as a developer error (code 10).
        return if ("10:" in text || "DEVELOPER_ERROR" in text) {
            "Gmail sign-in isn't set up for this build yet: register the package name and SHA-1 shown below " +
                "as an Android OAuth client in Google Cloud"
        } else {
            "Couldn't connect to Gmail: $text"
        }
    }

    private companion object {
        val PAN_REGEX = Regex("[A-Z]{5}[0-9]{4}[A-Z]")
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
    }
}
