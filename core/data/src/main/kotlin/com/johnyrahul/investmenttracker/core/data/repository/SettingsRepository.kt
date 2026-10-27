package com.johnyrahul.investmenttracker.core.data.repository

import com.johnyrahul.investmenttracker.core.data.db.GmailMessageEntity
import com.johnyrahul.investmenttracker.core.data.db.PortfolioDatabase
import com.johnyrahul.investmenttracker.core.data.db.SettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Small key-value settings, kept in the encrypted database because some (PAN, passwords) are sensitive. */
class SettingsRepository(private val db: PortfolioDatabase) {

    fun observe(): Flow<Map<String, String>> =
        db.settingDao().observeAll().map { rows -> rows.associate { it.key to it.value } }

    suspend fun get(key: String): String? = db.settingDao().get(key)

    suspend fun put(key: String, value: String?) {
        if (value.isNullOrEmpty()) db.settingDao().delete(key) else db.settingDao().put(SettingEntity(key, value))
    }

    /** PAN first (upper and lower case), then any other statement passwords the user saved. */
    suspend fun statementPasswords(): List<String> {
        val pan = get(PAN)?.trim().orEmpty()
        val extra = get(EXTRA_PASSWORDS).orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
        return (listOf(pan.uppercase(), pan.lowercase()).filter { it.isNotEmpty() } + extra).distinct()
    }

    suspend fun addStatementPassword(password: String) {
        val existing = get(EXTRA_PASSWORDS).orEmpty().lines().filter { it.isNotBlank() }
        if (password !in existing) put(EXTRA_PASSWORDS, (existing + password).joinToString("\n"))
        db.gmailMessageDao().forgetLocked()
    }

    suspend fun setPan(pan: String) {
        put(PAN, pan.trim().uppercase())
        db.gmailMessageDao().forgetLocked()
    }

    suspend fun processedGmailMessageIds(): Set<String> = db.gmailMessageDao().allIds().toSet()

    suspend fun markGmailMessage(messageId: String, outcome: String) {
        db.gmailMessageDao().upsert(GmailMessageEntity(messageId, outcome, System.currentTimeMillis()))
    }

    companion object {
        const val PAN = "pan"
        const val EXTRA_PASSWORDS = "extra_passwords"
        const val GMAIL_ENABLED = "gmail_enabled"
        const val GMAIL_STATUS = "gmail_status"
        const val GMAIL_LAST_CHECK = "gmail_last_check"
        const val PRICES_STATUS = "prices_status"
        const val PRICES_LAST_REFRESH = "prices_last_refresh"
    }
}
