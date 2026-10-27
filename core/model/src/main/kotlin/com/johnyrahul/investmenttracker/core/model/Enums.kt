package com.johnyrahul.investmenttracker.core.model

/** Where a holding is held. Extend as more brokers/platforms are added. */
enum class Platform(val displayName: String) {
    PAYTM_MONEY("Paytm Money"),
    GROWW("Groww"),
    OTHER("Other"),
    ;

    companion object {
        /** Guesses the platform from a depository participant / broker name. */
        fun fromBrokerName(name: String): Platform {
            val upper = name.uppercase()
            return when {
                "GROWW" in upper || "NEXTBILLION" in upper -> GROWW
                "PAYTM" in upper -> PAYTM_MONEY
                else -> OTHER
            }
        }
    }
}

enum class AssetType {
    MUTUAL_FUND,
    STOCK,
    ;

    companion object {
        /** Indian ISINs starting with INF are mutual fund / ETF units; everything else is treated as a stock. */
        fun fromIsin(isin: String): AssetType = if (isin.startsWith("INF")) MUTUAL_FUND else STOCK
    }
}

/** How a position is held, which decides which statements are authoritative for it. */
enum class HoldingCategory {
    /** Mutual fund units in a folio at CAMS / KFintech (how Groww and Paytm Money hold MFs). */
    MF_FOLIO,

    /** Shares, ETFs or MF units in a demat account at NSDL / CDSL. */
    DEMAT,
}

enum class TransactionType(val unitsSign: Int) {
    BUY(1),
    SELL(-1),
    SWITCH_IN(1),
    SWITCH_OUT(-1),
    DIVIDEND_REINVEST(1),
    BONUS(1),
}
