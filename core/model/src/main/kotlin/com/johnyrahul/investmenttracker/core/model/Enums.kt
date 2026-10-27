package com.johnyrahul.investmenttracker.core.model

/** Where a holding is held. Extend as more brokers/platforms are added. */
enum class Platform(val displayName: String) {
    PAYTM_MONEY("Paytm Money"),
    GROWW("Groww"),
    OTHER("Other"),
}

enum class AssetType { MUTUAL_FUND, STOCK }

enum class TransactionType(val unitsSign: Int) {
    BUY(1),
    SELL(-1),
    SWITCH_IN(1),
    SWITCH_OUT(-1),
    DIVIDEND_REINVEST(1),
    BONUS(1),
}
