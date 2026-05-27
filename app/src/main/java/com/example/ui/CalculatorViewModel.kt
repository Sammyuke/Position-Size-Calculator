package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface CalculatorUiState {
    object Idle : CalculatorUiState
    object Loading : CalculatorUiState
    data class Success(val results: CalculationResults) : CalculatorUiState
    data class Error(val message: String) : CalculatorUiState
}

data class CalculationResults(
    val riskAmount: Double,
    val positionSizeUnits: Double,
    val standardLots: Double,
    val miniLots: Double,
    val microLots: Double,
    val unitName: String
)

class CalculatorViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.tradingDao()

    // Active screen navigation state
    val currentTab = MutableStateFlow(0) // 0 = Calculator, 1 = Risk Journal & Accounts

    // All database accounts
    val accounts: StateFlow<List<TradingAccount>> = dao.getAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active selected account id
    private val _activeAccountId = MutableStateFlow<Long>(-1L)
    val activeAccountId: StateFlow<Long> = _activeAccountId.asStateFlow()

    // Active account
    val activeAccount: StateFlow<TradingAccount?> = combine(accounts, activeAccountId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Historical Trade Logs for the active account
    val tradeLogs: StateFlow<List<TradeLog>> = activeAccountId.flatMapLatest { id ->
        if (id != -1L) dao.getLogsForAccount(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // User inputs for the calculator
    val selectedAsset = MutableStateFlow(AssetCatalog.forexPairs[0])
    val accountBalance = MutableStateFlow("10000")
    val riskPercentageInput = MutableStateFlow("1.0")
    val riskAmountInput = MutableStateFlow("") // Optional direct dollar risk input
    val isDollarRiskMode = MutableStateFlow(false) // Toggle between % and $ risk
    val stopLossInput = MutableStateFlow("30") // Pips for Forex, points/distance for others
    val askPriceInput = MutableStateFlow("1.0850") // Used for cross-currency calculations when relevant

    // UI calculation status state
    private val _calcState = MutableStateFlow<CalculatorUiState>(CalculatorUiState.Idle)
    val calcState: StateFlow<CalculatorUiState> = _calcState.asStateFlow()

    // AI Chart Upload Image analysis states
    val isAnalyzingImage = MutableStateFlow(false)
    val imageAnalysisNotes = MutableStateFlow("")
    val uploadedBitmap = MutableStateFlow<Bitmap?>(null)

    // AI Risk recommendation states
    val isAnalyzingRisk = MutableStateFlow(false)
    val riskRecommendationText = MutableStateFlow("")
    val complianceStatus = MutableStateFlow("")

    init {
        // Initialize with default account if empty
        viewModelScope.launch {
            accounts.collectLatest { list ->
                if (list.isEmpty()) {
                    val defaultAccId = dao.insertAccount(
                        TradingAccount(
                            name = "$10k Prop Firm Account",
                            currency = "USD",
                            balance = 10000.0,
                            defaultRiskPercentage = 1.0
                        )
                    )
                    _activeAccountId.value = defaultAccId
                } else if (_activeAccountId.value == -1L) {
                    _activeAccountId.value = list.first().id
                }
            }
        }

        // Sync inputs when active account changes
        viewModelScope.launch {
            activeAccount.collectLatest { acc ->
                acc?.let {
                    accountBalance.value = String.format("%.2f", it.balance)
                    riskPercentageInput.value = it.defaultRiskPercentage.toString()
                }
            }
        }
    }

    fun selectAccount(accountId: Long) {
        _activeAccountId.value = accountId
    }

    /**
     * Creates a new account.
     */
    fun createNewAccount(name: String, currency: String, balance: Double, riskPlan: String, defaultRisk: Double) {
        viewModelScope.launch {
            val plan = riskPlan.ifBlank {
                "Risk $defaultRisk% per trade limit. Cut size after multiple losses."
            }
            val id = dao.insertAccount(
                TradingAccount(
                    name = name,
                    currency = currency,
                    balance = balance,
                    riskPlanDescription = plan,
                    defaultRiskPercentage = defaultRisk
                )
            )
            _activeAccountId.value = id
        }
    }

    /**
     * Deletes the currently active account.
     */
    fun deleteActiveAccount() {
        viewModelScope.launch {
            activeAccount.value?.let {
                dao.deleteAccount(it)
                _activeAccountId.value = -1L
            }
        }
    }

    /**
     * Updates the active account balance.
     */
    fun updateBalance(newBalance: Double) {
        viewModelScope.launch {
            activeAccount.value?.let {
                dao.updateAccount(it.copy(balance = newBalance))
            }
        }
    }

    /**
     * Updates the active account risk plan description.
     */
    fun updateRiskPlan(newPlan: String) {
        viewModelScope.launch {
            activeAccount.value?.let {
                dao.updateAccount(it.copy(riskPlanDescription = newPlan))
            }
        }
    }

    /**
     * Updates the default risk percentage.
     */
    fun updateDefaultRiskPercent(newPercent: Double) {
        viewModelScope.launch {
            activeAccount.value?.let {
                dao.updateAccount(it.copy(defaultRiskPercentage = newPercent))
            }
        }
    }

    /**
     * Logs the last calculation results to trade logs.
     */
    fun logCalculatedTrade(outcome: String = "PENDING") {
        val state = _calcState.value
        if (state is CalculatorUiState.Success) {
            val activeAccId = _activeAccountId.value
            if (activeAccId == -1L) return
            viewModelScope.launch {
                val results = state.results
                val currentAsset = selectedAsset.value
                val sLoss = stopLossInput.value.toDoubleOrNull() ?: 0.0
                val entryP = askPriceInput.value.toDoubleOrNull() ?: 1.0

                dao.insertLog(
                    TradeLog(
                        accountId = activeAccId,
                        pairName = currentAsset.pairName,
                        assetClass = currentAsset.assetClass,
                        riskAmount = results.riskAmount,
                        riskPercent = (results.riskAmount / (accountBalance.value.toDoubleOrNull() ?: 1.0)) * 100.0,
                        entryPrice = entryP,
                        stopLoss = sLoss,
                        calculatedSize = results.positionSizeUnits,
                        outcome = outcome,
                        notes = "Logged automatically by Position Size Calculator"
                    )
                )

                // Optionally deduct balance if it was a mock loss logging, or update
                // For position size logs, keep current balance in place.
            }
        }
    }

    /**
     * Deletes a trade log.
     */
    fun deleteTradeLog(log: TradeLog) {
        viewModelScope.launch {
            dao.deleteLog(log)
        }
    }

    /**
     * Updates the outcome of a logged trade (WIN / LOSS / PENDING).
     */
    fun updateTradeOutcome(log: TradeLog, outcome: String) {
        viewModelScope.launch {
            val updatedLog = log.copy(outcome = outcome)
            dao.updateLog(updatedLog)

            // Adjust account balance accordingly if user decides to sync
            val acc = activeAccount.value
            if (acc != null) {
                var newBalance = acc.balance
                if (log.outcome != outcome) {
                    // Revert old effect if there was any, and apply new
                    if (outcome == "LOSS") {
                        newBalance = acc.balance - log.riskAmount
                    } else if (outcome == "WIN") {
                        // Assuming standard Risk-to-Reward of 1:2 for estimate wins
                        newBalance = acc.balance + (log.riskAmount * 2.0)
                    }
                }
                dao.updateAccount(acc.copy(balance = newBalance))
                accountBalance.value = String.format("%.2f", newBalance)
            }
        }
    }

    /**
     * Performs position size calculations based on asset types and custom inputs.
     */
    fun calculatePositionSize() {
        _calcState.value = CalculatorUiState.Loading

        val balance = accountBalance.value.toDoubleOrNull() ?: 10000.0
        val isDollar = isDollarRiskMode.value
        val riskPercent = riskPercentageInput.value.toDoubleOrNull() ?: 1.0
        val riskAmountDirect = riskAmountInput.value.toDoubleOrNull() ?: 0.0

        val riskAmount = if (isDollar) {
            riskAmountDirect
        } else {
            balance * (riskPercent / 100.0)
        }

        val stopLossValue = stopLossInput.value.toDoubleOrNull() ?: 30.0
        if (stopLossValue <= 0.0 || riskAmount <= 0.0) {
            _calcState.value = CalculatorUiState.Error("Invalid Inputs: Stop loss and risk amount must be greater than 0.")
            return
        }

        val asset = selectedAsset.value

        try {
            var unitsSize = 0.0
            var unitDisplayName = "Units"

            when (asset.assetClass) {
                "Forex" -> {
                    // Standard Forex equation:
                    // Pip Value = Account Currency Pip Value
                    // For typical USD quote pairs: EUR/USD, GBP/USD, AUD/USD:
                    // Standard Pip Value = $10 per Lot (100,000 units), meaning 1 pip = 0.0001 per unit.
                    // For cross pairs or other rates, we assume standard direct pip matching or quote rates.
                    // Risk Amount = Units * StopLoss(pips) * PipMultiplier
                    // Units = Risk Amount / (StopLoss * PipMultiplier)
                    val pipValMultiplier = asset.pipMultiplier
                    unitsSize = riskAmount / (stopLossValue * pipValMultiplier)
                    unitDisplayName = "Units"
                }
                "Crypto" -> {
                    // Crypto: stopLoss is absolute price difference.
                    // Units = Risk Amount / Stop Loss price distance.
                    unitsSize = riskAmount / stopLossValue
                    unitDisplayName = asset.pairName.substringBefore("/")
                }
                "Metals" -> {
                    // Metals (e.g. Gold): units are Ounces (oz)
                    // Units = Risk Amount / Stop Loss distance.
                    unitsSize = riskAmount / stopLossValue
                    unitDisplayName = "Ounces"
                }
                "Oils" -> {
                    // Oils: units are Barrels (bbl)
                    // Units = Risk Amount / Stop Loss distance.
                    unitsSize = riskAmount / stopLossValue
                    unitDisplayName = "Barrels"
                }
                "Stocks" -> {
                    // Stocks: units are Shares
                    // Units = Risk Amount / Stop Loss distance.
                    unitsSize = riskAmount / stopLossValue
                    unitDisplayName = "Shares"
                }
            }

            // Standard MT4/MT5 contract sizing mapping:
            // Forex: 1 Standard Lot = 100,000 units.
            // Crypto: 1 Standard Lot = 1 coin.
            // Metals: 1 Standard Lot Gold = 100 Ounces.
            // Oils: 1 Standard Lot Oil = 1,000 Barrels.
            // Stocks: 1 standard lot = 1 item (usually priced per share).
            val lotDivisor = asset.contractSize
            val standardLotsCalculated = unitsSize / lotDivisor
            val miniLotsCalculated = unitsSize / (lotDivisor / 10.0)
            val microLotsCalculated = unitsSize / (lotDivisor / 100.0)

            _calcState.value = CalculatorUiState.Success(
                CalculationResults(
                    riskAmount = riskAmount,
                    positionSizeUnits = unitsSize,
                    standardLots = standardLotsCalculated,
                    miniLots = miniLotsCalculated,
                    microLots = microLotsCalculated,
                    unitName = unitDisplayName
                )
            )
        } catch (e: Exception) {
            _calcState.value = CalculatorUiState.Error("Calculation failed: ${e.message}")
        }
    }

    /**
     * Upload and analyze screenshot with AI
     */
    fun analyzeChartImageWithAI(bitmap: Bitmap) {
        uploadedBitmap.value = bitmap
        isAnalyzingImage.value = true
        imageAnalysisNotes.value = ""

        viewModelScope.launch {
            val result = GeminiService.analyzeChartImage(bitmap)
            isAnalyzingImage.value = false

            if (result != null) {
                // Pre-populate manual inputs dynamically
                val match = AssetCatalog.allAssets.firstOrNull {
                    it.pairName.startsWith(result.pairName, ignoreCase = true) ||
                    result.pairName.startsWith(it.pairName, ignoreCase = true)
                } ?: AssetCatalog.allAssets.firstOrNull {
                    it.pairName.contains(result.pairName, ignoreCase = true)
                }

                if (match != null) {
                    selectedAsset.value = match
                } else {
                    // Dynamically create temporary/ad-hoc matching asset structure
                    selectedAsset.value = TradingAsset(
                        result.pairName,
                        result.assetClass,
                        isJpyOrMetal = result.pairName.contains("JPY") || result.pairName.contains("XAU"),
                        pipMultiplier = if (result.assetClass == "Forex") 0.0001 else 1.0,
                        contractSize = when (result.assetClass) {
                            "Forex" -> 100000.0
                            "Crypto" -> 1.0
                            "Metals" -> 100.0
                            "Oils" -> 1000.0
                            else -> 1.0
                        }
                    )
                }

                stopLossInput.value = result.stopLoss.toString()
                askPriceInput.value = result.currentPrice.toString()
                imageAnalysisNotes.value = result.reasoning

                // Recalculate automatic results
                calculatePositionSize()
            } else {
                imageAnalysisNotes.value = "AI analysis failed or returned empty results. Please verify your Gemini API Key in the settings or check network connection."
            }
        }
    }

    /**
     * Let Gemini analyze account plan + trade outcome history to advice on risk profile.
     */
    fun auditRiskWithAI() {
        val plan = activeAccount.value?.riskPlanDescription ?: ""
        val history = tradeLogs.value
        val bal = accountBalance.value.toDoubleOrNull() ?: 10000.0

        isAnalyzingRisk.value = true
        riskRecommendationText.value = ""
        complianceStatus.value = ""

        viewModelScope.launch {
            val r = GeminiService.recommendRisk(plan, history, bal)
            isAnalyzingRisk.value = false

            if (r != null) {
                riskRecommendationText.value = r.analysis
                complianceStatus.value = r.complianceRating

                // Pre-populate input percentage if compliant recommendation is suggested
                riskPercentageInput.value = r.recommendedRiskPercent.toString()
                riskAmountInput.value = r.recommendedRiskAmount.toString()
            } else {
                riskRecommendationText.value = "AI risk advisor failed. Make sure your Gemini API Key is entered."
            }
        }
    }
}
