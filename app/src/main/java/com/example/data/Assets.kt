package com.example.data

data class TradingAsset(
    val pairName: String,
    val assetClass: String, // "Forex", "Crypto", "Metals", "Oils", "Stocks"
    val isJpyOrMetal: Boolean = false,
    val pipMultiplier: Double = 0.0001, // 0.0001 for Forex, 0.01 for JPY/Gold
    val contractSize: Double = 100000.0 // 100k for Forex, 1 for Crypto/Shares, 100 for Gold, 1000 for Oil
)

object AssetCatalog {
    val forexPairs = listOf(
        TradingAsset("EUR/USD", "Forex", isJpyOrMetal = false, pipMultiplier = 0.0001, contractSize = 100000.0),
        TradingAsset("GBP/USD", "Forex", isJpyOrMetal = false, pipMultiplier = 0.0001, contractSize = 100000.0),
        TradingAsset("AUD/USD", "Forex", isJpyOrMetal = false, pipMultiplier = 0.0001, contractSize = 100000.0),
        TradingAsset("NZD/USD", "Forex", isJpyOrMetal = false, pipMultiplier = 0.0001, contractSize = 100000.0),
        TradingAsset("USD/JPY", "Forex", isJpyOrMetal = true, pipMultiplier = 0.01, contractSize = 100000.0),
        TradingAsset("EUR/JPY", "Forex", isJpyOrMetal = true, pipMultiplier = 0.01, contractSize = 100000.0),
        TradingAsset("GBP/JPY", "Forex", isJpyOrMetal = true, pipMultiplier = 0.01, contractSize = 100000.0),
        TradingAsset("USD/CAD", "Forex", isJpyOrMetal = false, pipMultiplier = 0.0001, contractSize = 100000.0),
        TradingAsset("USD/CHF", "Forex", isJpyOrMetal = false, pipMultiplier = 0.0001, contractSize = 100000.0),
        TradingAsset("EUR/GBP", "Forex", isJpyOrMetal = false, pipMultiplier = 0.0001, contractSize = 100000.0),
        TradingAsset("EUR/AUD", "Forex", isJpyOrMetal = false, pipMultiplier = 0.0001, contractSize = 100000.0),
        TradingAsset("GBP/AUD", "Forex", isJpyOrMetal = false, pipMultiplier = 0.0001, contractSize = 100000.0)
    )

    val cryptoPairs = listOf(
        TradingAsset("BTC/USD", "Crypto", isJpyOrMetal = false, pipMultiplier = 1.0, contractSize = 1.0),
        TradingAsset("ETH/USD", "Crypto", isJpyOrMetal = false, pipMultiplier = 1.0, contractSize = 1.0),
        TradingAsset("SOL/USD", "Crypto", isJpyOrMetal = false, pipMultiplier = 1.0, contractSize = 1.0),
        TradingAsset("SHIB/USD", "Crypto", isJpyOrMetal = false, pipMultiplier = 0.000001, contractSize = 1000000.0),
        TradingAsset("DOGE/USD", "Crypto", isJpyOrMetal = false, pipMultiplier = 0.001, contractSize = 10000.0),
        TradingAsset("XRP/USD", "Crypto", isJpyOrMetal = false, pipMultiplier = 0.001, contractSize = 1000.0),
        TradingAsset("ADA/USD", "Crypto", isJpyOrMetal = false, pipMultiplier = 0.001, contractSize = 1000.0)
    )

    val metals = listOf(
        TradingAsset("XAU/USD (Gold)", "Metals", isJpyOrMetal = true, pipMultiplier = 0.01, contractSize = 100.0),
        TradingAsset("XAG/USD (Silver)", "Metals", isJpyOrMetal = true, pipMultiplier = 0.01, contractSize = 5000.0),
        TradingAsset("XPT/USD (Platinum)", "Metals", isJpyOrMetal = true, pipMultiplier = 0.01, contractSize = 100.0)
    )

    val oils = listOf(
        TradingAsset("US OIL (WTI)", "Oils", isJpyOrMetal = false, pipMultiplier = 1.0, contractSize = 1000.0),
        TradingAsset("UK OIL (Brent)", "Oils", isJpyOrMetal = false, pipMultiplier = 1.0, contractSize = 1000.0)
    )

    val stocks = listOf(
        TradingAsset("AAPL (Apple)", "Stocks", isJpyOrMetal = false, pipMultiplier = 1.0, contractSize = 1.0),
        TradingAsset("TSLA (Tesla)", "Stocks", isJpyOrMetal = false, pipMultiplier = 1.0, contractSize = 1.0),
        TradingAsset("MSFT (Microsoft)", "Stocks", isJpyOrMetal = false, pipMultiplier = 1.0, contractSize = 1.0),
        TradingAsset("AMZN (Amazon)", "Stocks", isJpyOrMetal = false, pipMultiplier = 1.0, contractSize = 1.0),
        TradingAsset("GOOGL (Alphabet)", "Stocks", isJpyOrMetal = false, pipMultiplier = 1.0, contractSize = 1.0),
        TradingAsset("NVDA (Nvidia)", "Stocks", isJpyOrMetal = false, pipMultiplier = 1.0, contractSize = 1.0),
        TradingAsset("META (Meta Platforms)", "Stocks", isJpyOrMetal = false, pipMultiplier = 1.0, contractSize = 1.0),
        TradingAsset("NFLX (Netflix)", "Stocks", isJpyOrMetal = false, pipMultiplier = 1.0, contractSize = 1.0)
    )

    val allAssets = forexPairs + cryptoPairs + metals + oils + stocks

    fun getAssetByPair(pair: String): TradingAsset {
        return allAssets.firstOrNull { it.pairName.equals(pair, ignoreCase = true) }
            ?: TradingAsset(pair, "Forex", false, 0.0001, 100000.0)
    }
}
