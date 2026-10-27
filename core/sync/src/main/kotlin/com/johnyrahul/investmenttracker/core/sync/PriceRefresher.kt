package com.johnyrahul.investmenttracker.core.sync

import com.johnyrahul.investmenttracker.core.data.repository.PortfolioRepository
import com.johnyrahul.investmenttracker.core.data.repository.SettingsRepository
import com.johnyrahul.investmenttracker.core.model.AssetType
import com.johnyrahul.investmenttracker.core.model.PricePoint
import com.johnyrahul.investmenttracker.core.parsing.AmfiNavParser
import com.johnyrahul.investmenttracker.core.parsing.BhavcopyParser
import com.johnyrahul.investmenttracker.core.parsing.ClosePrice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.zip.ZipInputStream

/**
 * Downloads public end-of-day prices: mutual fund NAVs from AMFI and stock / ETF closing prices
 * from the NSE (falling back to BSE) bhavcopy. Only public files are downloaded; nothing about the
 * portfolio is sent anywhere.
 */
class PriceRefresher(
    private val portfolio: PortfolioRepository,
    private val settings: SettingsRepository,
) {

    sealed interface Result {
        data class Done(val updated: Int, val missing: Int) : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun refresh(): Result = withContext(Dispatchers.IO) {
        val result = try {
            refreshPrices()
        } catch (e: Exception) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
        val now = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())
        settings.put(
            SettingsRepository.PRICES_STATUS,
            when (result) {
                is Result.Done -> "Updated $now: ${result.updated} prices" +
                    if (result.missing > 0) " (${result.missing} not found, using statement price)" else ""
                is Result.Failed -> "Last refresh failed ($now): ${result.reason}"
            },
        )
        if (result is Result.Done) settings.put(SettingsRepository.PRICES_LAST_REFRESH, System.currentTimeMillis().toString())
        result
    }

    private suspend fun refreshPrices(): Result {
        val instruments = portfolio.trackedInstruments()
        if (instruments.isEmpty()) return Result.Done(0, 0)
        val prices = LinkedHashMap<String, PricePoint>()
        val amfiCodes = HashMap<String, String>()
        val symbols = HashMap<String, String>()
        val errors = mutableListOf<String>()

        val funds = instruments.filter { it.type == AssetType.MUTUAL_FUND }.mapTo(HashSet()) { it.isin }
        if (funds.isNotEmpty()) {
            runCatching {
                httpGet(AMFI_URL) { stream ->
                    AmfiNavParser.parse(stream.bufferedReader().lineSequence()).forEach { nav ->
                        for (isin in nav.isins) if (isin in funds) {
                            prices[isin] = PricePoint(isin, nav.date, nav.nav)
                            amfiCodes[isin] = nav.schemeCode
                        }
                    }
                }
            }.onFailure { errors += "AMFI: ${it.message}" }
        }

        // Stocks, plus any fund AMFI did not list.
        val listed = instruments.filter { it.type == AssetType.STOCK || it.isin !in prices }.mapTo(HashSet()) { it.isin }
        if (listed.isNotEmpty()) {
            val nse = runCatching { latestBhavcopy(NSE) }.onFailure { errors += "NSE: ${it.message}" }.getOrNull().orEmpty()
            val onNse = nse.mapTo(HashSet()) { it.isin }
            val bse = if (listed.any { it !in onNse }) {
                runCatching { latestBhavcopy(BSE) }.onFailure { errors += "BSE: ${it.message}" }.getOrNull().orEmpty()
            } else {
                emptyList()
            }
            val closes = nse + bse
            // Apply BSE first and NSE last, so NSE wins where a stock trades on both.
            for (close in closes.reversed()) if (close.isin in listed) {
                prices[close.isin] = PricePoint(close.isin, close.date, close.close)
                if (close.symbol.isNotEmpty()) symbols[close.isin] = close.symbol
            }
        }

        if (prices.isEmpty() && errors.isNotEmpty()) return Result.Failed(errors.joinToString("; "))
        portfolio.savePrices(prices.values.toList(), amfiCodes, symbols)
        return Result.Done(prices.size, instruments.size - prices.size)
    }

    private fun latestBhavcopy(exchange: String): List<ClosePrice> {
        val today = LocalDate.now(IST)
        // Walk back over weekends and market holidays; files appear in the evening.
        for (daysBack in 0..7) {
            val date = today.minusDays(daysBack.toLong())
            val stamp = date.format(DateTimeFormatter.BASIC_ISO_DATE)
            try {
                return if (exchange == NSE) {
                    httpGet(NSE_URL.format(stamp), mapOf("Referer" to "https://www.nseindia.com/")) { stream ->
                        ZipInputStream(stream).use { zip ->
                            if (zip.nextEntry == null) emptyList() else BhavcopyParser.parse(zip.bufferedReader().lineSequence())
                        }
                    }
                } else {
                    httpGet(BSE_URL.format(stamp), mapOf("Referer" to "https://www.bseindia.com/")) { stream ->
                        BhavcopyParser.parse(stream.bufferedReader().lineSequence())
                    }
                }
            } catch (e: HttpStatusException) {
                if (e.code != 404 && e.code != 403) throw e
            }
        }
        return emptyList()
    }

    private companion object {
        val IST: ZoneId = ZoneId.of("Asia/Kolkata")
        const val NSE = "NSE"
        const val BSE = "BSE"
        const val AMFI_URL = "https://www.amfiindia.com/spages/NAVAll.txt"
        const val NSE_URL = "https://nsearchives.nseindia.com/content/cm/BhavCopy_NSE_CM_0_0_0_%s_F_0000.csv.zip"
        const val BSE_URL = "https://www.bseindia.com/download/BhavCopy/Equity/BhavCopy_BSE_CM_0_0_0_%s_F_0000.CSV"
    }
}
