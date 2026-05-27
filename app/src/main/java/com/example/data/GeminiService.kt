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
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Data structures for JSON communication
    data class GeminiRequest(val contents: List<Content>)
    data class Content(val parts: List<Part>)
    data class Part(val text: String? = null, val inlineData: InlineData? = null)
    data class InlineData(val mimeType: String, val data: String)

    // Result structures for parsing
    data class ChartAnalysisResult(
        val pairName: String,
        val assetClass: String, // "Forex", "Crypto", "Metals", "Oils", "Stocks"
        val currentPrice: Double,
        val entryPrice: Double,
        val stopLoss: Double, // pips distance for Forex, price distance for others
        val reasoning: String
    )

    data class RiskRecommendationResult(
        val recommendedRiskPercent: Double,
        val recommendedRiskAmount: Double,
        val analysis: String,
        val complianceRating: String
    )

    /**
     * Converts a Bitmap to a base64 JPEG string.
     */
    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Analyzes a trading chart screenshot using Gemini API.
     */
    suspend fun analyzeChartImage(bitmap: Bitmap): ChartAnalysisResult? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "GEMINI_API_KEY") {
            Log.e(TAG, "API Key is missing or default placeholder!")
            return@withContext null
        }

        val base64Image = bitmap.toBase64()
        val prompt = """
            You are an expert trading assistant. Analyze this screenshot of a trading chart and return structural information to pre-populate a position size calculator.
            Determine:
            1. Asset/Pair Name (e.g. BTC/USD, EUR/USD, GBP/USD, XAU/USD, USOIL, TSLA, AAPL, Solana, etc. Make it uppercase. Convert standard pairs correctly.)
            2. Asset Class (MUST be exactly one of: "Forex", "Crypto", "Metals", "Oils", "Stocks")
            3. Current Market Ask/Bid Price shown in the chart.
            4. Recommended Entry price.
            5. Recommended Stop Loss distance.
               - For Forex, this is in standard pips (e.g. 50 pips, 20 pips, 100 pips).
               - For non-Forex (Crypto, Stocks, Metals, Oils), this is the absolute entry-to-stop price distance (e.g. if BTC entry is 60250 and stop is 59000, distance is 1250.0).
            
            Respond ONLY with a single JSON block containing these exact keys:
            {
              "pairName": "EUR/USD",
              "assetClass": "Forex",
              "currentPrice": 1.0852,
              "entryPrice": 1.0850,
              "stopLoss": 35.0,
              "reasoning": "Dynamic support lines identified at 1.0815. Entry placed on pullback."
            }
        """.trimIndent()

        val req = GeminiRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            )
        )

        val adapter = moshi.adapter(GeminiRequest::class.java)
        val jsonRequest = adapter.toJson(req)

        try {
            val url = "$BASE_URL?key=$apiKey"
            val requestBody = jsonRequest.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini call failed with code ${response.code}: $responseBody")
                return@withContext null
            }

            val rawJson = extractJsonFromResponse(responseBody) ?: return@withContext null
            Log.d(TAG, "Extracted parsed JSON: $rawJson")

            return@withContext moshi.adapter(ChartAnalysisResult::class.java).fromJson(rawJson)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during analyzeChartImage", e)
            null
        }
    }

    /**
     * Gets risk advisory recommendation based on account plan & history logs.
     */
    suspend fun recommendRisk(
        riskPlan: String,
        recentLogs: List<TradeLog>,
        accountBalance: Double
    ): RiskRecommendationResult? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "GEMINI_API_KEY") {
            Log.e(TAG, "API Key is missing or placeholder!")
            return@withContext null
        }

        val logsSummary = if (recentLogs.isEmpty()) {
            "No historical trades logged yet."
        } else {
            recentLogs.joinToString("\n") { log ->
                "Date: ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(log.timestamp))}, Pair: ${log.pairName}, Risk %: ${log.riskPercent}%, Risk $: $${log.riskAmount}, Outcome: ${log.outcome}"
            }
        }

        val prompt = """
            You are an expert Risk Management advisor and Trading Coach.
            Review the user's Account Risk Management Plan and their recent Trade History logs on this account.
            
            USER RISK PLAN:
            $riskPlan
            
            ACCOUNT CURRENT BALANCE: ${'$'}$accountBalance
            
            RECENT TRADE HISTORY (Newest to Oldest):
            $logsSummary
            
            Analyze the compliance level of the user (e.g. are they exceeding their defined risk thresholds, losing and getting emotional, or consistently executing sound risk rules?).
            Suggest the exact optimal Risk Percentage (%) and Risk Dollars ($) they should risk on their *next* trade based on current conditions to keep their account alive and growing.
            
            Respond ONLY with a single JSON block containing these exact keys:
            {
              "recommendedRiskPercent": 1.0,
              "recommendedRiskAmount": 100.0,
              "analysis": "Based on your risk plan, since you suffered a loss on your last trade on EUR/USD, you should stick to your 1% rule or reduce risk to 0.5% for safety.",
              "complianceRating": "Fully Compliant" (or "Violations Found" / "Needs Caution")
            }
        """.trimIndent()

        val req = GeminiRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            )
        )

        val adapter = moshi.adapter(GeminiRequest::class.java)
        val jsonRequest = adapter.toJson(req)

        try {
            val url = "$BASE_URL?key=$apiKey"
            val requestBody = jsonRequest.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini recommendation failed with code ${response.code}: $responseBody")
                return@withContext null
            }

            val rawJson = extractJsonFromResponse(responseBody) ?: return@withContext null
            return@withContext moshi.adapter(RiskRecommendationResult::class.java).fromJson(rawJson)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during recommendRisk", e)
            null
        }
    }

    /**
     * Extracts pure JSON block from Markdown-wrapped block response.
     */
    private fun extractJsonFromResponse(response: String): String? {
        try {
            // Find candidates array block or the text content
            val startOfText = response.indexOf("\"text\":")
            if (startOfText == -1) return null
            
            // Let's do a simple regex or substring parsing on response text
            val textRegex = Regex("\"text\"\\s*:\\s*\"([\\s\\S]*?)\"\\s*[},]")
            val match = textRegex.find(response)
            var textBody = match?.groupValues?.get(1) ?: return null
            
            // Clean escaped characters in JSON string
            textBody = textBody.replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\t", "\t")

            val jsonStartIndex = textBody.indexOf("{")
            val jsonEndIndex = textBody.lastIndexOf("}")
            if (jsonStartIndex != -1 && jsonEndIndex != -1) {
                return textBody.substring(jsonStartIndex, jsonEndIndex + 1)
            }
            return textBody.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed extracting JSON from response: $response", e)
            return null
        }
    }
}
