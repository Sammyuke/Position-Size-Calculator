package com.example.data

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    private const val BASE_URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    data class ChartAnalysisResult(
        val pairName: String,
        val assetClass: String,
        val currentPrice: Double,
        val entryPrice: Double,
        val stopLoss: Double,
        val reasoning: String
    )

    data class RiskRecommendationResult(
        val recommendedRiskPercent: Double,
        val recommendedRiskAmount: Double,
        val analysis: String,
        val complianceRating: String
    )

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun getApiKey(): String? {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isEmpty() || key == "MY_GEMINI_API_KEY" || key == "GEMINI_API_KEY") {
            Log.e(TAG, "API Key is missing or placeholder!")
            return null
        }
        return key
    }

    private fun buildGroqRequest(prompt: String): String {
        return """{"model":"$MODEL","messages":[{"role":"user","content":"${ prompt.replace("\"","\\\"").replace("\n","\\n") }"}],"temperature":0.3}"""
    }

    private fun callGroq(prompt: String, apiKey: String): String? {
        return try {
            val body = buildGroqRequest(prompt).toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e(TAG, "Groq call failed ${response.code}: $responseBody")
                return null
            }
            extractTextFromGroqResponse(responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling Groq", e)
            null
        }
    }

    private fun extractTextFromGroqResponse(response: String): String? {
        return try {
            val marker = "\"content\":"
            val start = response.indexOf(marker)
            if (start == -1) return null
            val contentStart = response.indexOf("\"", start + marker.length) + 1
            val contentEnd = response.lastIndexOf("\"")
            if (contentStart <= 0 || contentEnd <= contentStart) return null
            response.substring(contentStart, contentEnd)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        } catch (e: Exception) {
            Log.e(TAG, "Failed extracting Groq response", e)
            null
        }
    }

    private fun extractJson(text: String): String? {
        val start = text.indexOf("{")
        val end = text.lastIndexOf("}")
        return if (start != -1 && end != -1) text.substring(start, end + 1) else null
    }

    suspend fun analyzeChartImage(bitmap: Bitmap): ChartAnalysisResult? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey() ?: return@withContext null

        val prompt = """
            You are an expert trading assistant. Analyze this trading chart description and return JSON to pre-populate a position size calculator.
            Determine:
            1. Asset/Pair Name (e.g. BTC/USD, EUR/USD, XAU/USD)
            2. Asset Class (MUST be exactly one of: "Forex", "Crypto", "Metals", "Oils", "Stocks")
            3. Current Market Price
            4. Recommended Entry price
            5. Stop Loss distance (pips for Forex, price distance for others)
            Respond ONLY with this JSON:
            {"pairName":"EUR/USD","assetClass":"Forex","currentPrice":1.0852,"entryPrice":1.0850,"stopLoss":35.0,"reasoning":"Support identified at 1.0815."}
        """.trimIndent()

        val text = callGroq(prompt, apiKey) ?: return@withContext null
        val json = extractJson(text) ?: return@withContext null
        try {
            moshi.adapter(ChartAnalysisResult::class.java).fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing chart result", e)
            null
        }
    }

    suspend fun recommendRisk(
        riskPlan: String,
        recentLogs: List<TradeLog>,
        accountBalance: Double
    ): RiskRecommendationResult? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey() ?: return@withContext null

        val logsSummary = if (recentLogs.isEmpty()) {
            "No historical trades logged yet."
        } else {
            recentLogs.joinToString("\n") { log ->
                "Date: ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(log.timestamp))}, Pair: ${log.pairName}, Risk%: ${log.riskPercent}%, Risk$: $${log.riskAmount}, Outcome: ${log.outcome}"
            }
        }

        val prompt = """
            You are an expert Risk Management advisor.
            USER RISK PLAN: $riskPlan
            ACCOUNT BALANCE: $accountBalance
            RECENT TRADES: $logsSummary
            Suggest the optimal risk for the next trade.
            Respond ONLY with this JSON:
            {"recommendedRiskPercent":1.0,"recommendedRiskAmount":100.0,"analysis":"Your analysis here.","complianceRating":"Fully Compliant"}
        """.trimIndent()

        val text = callGroq(prompt, apiKey) ?: return@withContext null
        val json = extractJson(text) ?: return@withContext null
        try {
            moshi.adapter(RiskRecommendationResult::class.java).fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing risk result", e)
            null
        }
    }
}
