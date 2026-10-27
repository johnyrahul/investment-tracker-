package com.johnyrahul.investmenttracker.core.sync

import com.johnyrahul.investmenttracker.core.data.repository.ImportOutcome
import com.johnyrahul.investmenttracker.core.data.repository.PortfolioRepository
import com.johnyrahul.investmenttracker.core.data.repository.SettingsRepository
import com.johnyrahul.investmenttracker.core.data.repository.StatementRecord
import com.johnyrahul.investmenttracker.core.parsing.CasParser
import com.johnyrahul.investmenttracker.core.parsing.UnsupportedStatementException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

sealed interface ImportResult {
    data class Imported(val statement: StatementRecord) : ImportResult
    data object AlreadyImported : ImportResult

    /** None of the saved passwords (PAN etc.) opened the PDF. */
    data object NeedsPassword : ImportResult
    data class Unsupported(val reason: String) : ImportResult
    data class Failed(val reason: String) : ImportResult
}

/** Unlock, parse and save one CAS PDF, entirely on the device. */
class StatementImporter(
    private val reader: PdfStatementReader,
    private val portfolio: PortfolioRepository,
    private val settings: SettingsRepository,
) {

    suspend fun import(pdf: ByteArray, origin: String, password: String? = null): ImportResult = withContext(Dispatchers.IO) {
        try {
            val hash = sha256(pdf)
            if (portfolio.isImported(hash)) return@withContext ImportResult.AlreadyImported

            val passwords = listOfNotNull(password?.takeIf { it.isNotEmpty() }) + settings.statementPasswords()
            val text = when (val result = reader.read(pdf, passwords)) {
                is PdfStatementReader.Result.Locked -> return@withContext ImportResult.NeedsPassword
                is PdfStatementReader.Result.Text -> {
                    // Remember a password the user typed for this file so the Gmail sync can use it too.
                    if (password != null && result.password == password) settings.addStatementPassword(password)
                    result.text
                }
            }
            when (val outcome = portfolio.importStatement(CasParser.parse(text), hash, origin)) {
                is ImportOutcome.Imported -> ImportResult.Imported(outcome.statement)
                ImportOutcome.AlreadyImported -> ImportResult.AlreadyImported
            }
        } catch (e: UnsupportedStatementException) {
            ImportResult.Unsupported(e.message ?: "Not a supported statement")
        } catch (e: Exception) {
            ImportResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
