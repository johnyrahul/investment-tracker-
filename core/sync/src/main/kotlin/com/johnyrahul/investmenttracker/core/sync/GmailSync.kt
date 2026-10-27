package com.johnyrahul.investmenttracker.core.sync

import android.content.Context
import com.johnyrahul.investmenttracker.core.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Finds CAS emails in Gmail and imports their PDFs. Runs daily in the background and on demand. */
class GmailSync(
    private val context: Context,
    private val importer: StatementImporter,
    private val settings: SettingsRepository,
) {

    sealed interface Result {
        data class Done(val imported: Int, val locked: Int) : Result
        data object NotEnabled : Result
        data object NeedsConsent : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun sync(): Result = withContext(Dispatchers.IO) {
        if (settings.get(SettingsRepository.GMAIL_ENABLED) != "true") return@withContext Result.NotEnabled
        val result = try {
            when (val auth = GmailAuth.authorize(context)) {
                is GmailAuth.Result.NeedsConsent -> Result.NeedsConsent
                is GmailAuth.Result.Token -> fetch(GmailClient(auth.accessToken))
            }
        } catch (e: HttpStatusException) {
            Result.Failed(if (e.code == 401 || e.code == 403) "Gmail refused access (HTTP ${e.code})" else e.message.orEmpty())
        } catch (e: Exception) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
        val now = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())
        val status = when (result) {
            is Result.Done -> buildString {
                append("Checked $now: ")
                append(if (result.imported == 0) "no new statements" else "${result.imported} new statement(s) imported")
                if (result.locked > 0) append(", ${result.locked} couldn't be opened with your PAN or saved passwords")
            }
            Result.NeedsConsent -> "Gmail access needs to be granted again"
            is Result.Failed -> "Last check failed ($now): ${result.reason}"
            Result.NotEnabled -> null
        }
        settings.put(SettingsRepository.GMAIL_STATUS, status)
        if (result is Result.Done) settings.put(SettingsRepository.GMAIL_LAST_CHECK, System.currentTimeMillis().toString())
        result
    }

    private suspend fun fetch(gmail: GmailClient): Result {
        val seen = settings.processedGmailMessageIds()
        val ids = gmail.searchMessageIds(QUERY, limit = 200).filter { it !in seen }
        var imported = 0
        var locked = 0
        // Gmail lists newest first; import oldest first so the history reads naturally.
        for (id in ids.reversed()) {
            var outcome = "unsupported"
            for (attachment in gmail.pdfAttachments(id)) {
                val result = importer.import(attachment.bytes, origin = "gmail")
                outcome = when (result) {
                    is ImportResult.Imported -> "imported".also { imported++ }
                    ImportResult.AlreadyImported -> "duplicate"
                    ImportResult.NeedsPassword -> "locked".also { locked++ }
                    is ImportResult.Unsupported -> outcome
                    // Transient failures are retried on the next run.
                    is ImportResult.Failed -> "failed"
                }
            }
            if (outcome != "failed") settings.markGmailMessage(id, outcome)
        }
        return Result.Done(imported, locked)
    }

    companion object {
        /**
         * CAS emails from the depositories (monthly, PAN-protected) and from CAMS / KFintech
         * (on request). Anything that turns out not to be a CAS is skipped and remembered.
         */
        const val QUERY = "has:attachment filename:pdf newer_than:400d " +
            "{subject:\"consolidated account statement\" subject:CAS from:cdslstatement.com " +
            "from:nsdl.co.in from:camsonline.com from:kfintech.com}"
    }
}
