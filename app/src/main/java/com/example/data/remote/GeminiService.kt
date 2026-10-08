package com.example.data.remote

import android.graphics.Bitmap
import com.example.BuildConfig
import com.example.data.local.dao.SessionWithDresses
import com.example.util.ImageStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AiDressAnalysisResult(
    val matchedCustomerId: Long?,
    val matchedCustomerName: String,
    val matchedCustomerPhone: String,
    val matchedSessionId: Long?,
    val matchedSessionCode: String,
    val matchedDressId: Long?,
    val confidence: Int,
    val garmentDescription: String,
    val matchReason: String,
    val alternativeMatches: List<String> = emptyList()
)

class GeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun analyzeAndMatchDress(
        queryBitmap: Bitmap,
        sessionsWithDresses: List<SessionWithDresses>
    ): Result<AiDressAnalysisResult> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please add GEMINI_API_KEY to the AI Studio Secrets panel.")
            )
        }

        try {
            val queryBase64 = ImageStorageHelper.bitmapToBase64(queryBitmap)

            // Construct catalog metadata for Gemini
            val catalogBuilder = StringBuilder()
            catalogBuilder.append("CURRENT LAUNDRY DATABASE CATALOG:\n")
            if (sessionsWithDresses.isEmpty()) {
                catalogBuilder.append("No active sessions currently in database.\n")
            } else {
                for (item in sessionsWithDresses) {
                    val s = item.session
                    catalogBuilder.append("- Session Code: ${s.sessionCode} (ID: ${s.id})\n")
                    catalogBuilder.append("  Customer: ${s.customerName} (ID: ${s.customerId}, Phone: ${s.customerPhone})\n")
                    catalogBuilder.append("  Status: ${if (s.isCompleted) "Completed" else "Open/In-Progress"}\n")
                    catalogBuilder.append("  Service: ${s.serviceType}, Notes: ${s.notes.ifBlank { "None" }}\n")
                    catalogBuilder.append("  Dresses Recorded (${item.dresses.size}):\n")
                    for (d in item.dresses) {
                        catalogBuilder.append("    * Dress ID: ${d.id}, Label: '${d.dressName}', Color: '${d.color}', Category: '${d.category}'\n")
                    }
                }
            }

            val prompt = """
                You are an expert laundry AI assistant. A worker has snapped a picture of an untagged or misplaced garment/dress.
                Task:
                1. Inspect the provided image in detail: identify garment category, silhouette, exact colors, pattern/print (e.g., floral, polka dot, solid, striped), fabric style, neck/sleeve, embellishments.
                2. Cross-reference this garment against the current customer database records provided below.
                3. Determine which customer and session this dress belongs to. Even if dresses in the database have basic labels (like 'Floral dress', 'Blue evening gown', 'Silk blouse'), use visual deductions and match them against the most probable session.
                4. Return a strict JSON object with:
                   - "garmentDescription": detailed physical description of the scanned garment.
                   - "matchedCustomerId": numeric ID of the best customer match (or null if no plausible match).
                   - "matchedCustomerName": name of the matched customer.
                   - "matchedCustomerPhone": phone of the matched customer.
                   - "matchedSessionId": numeric ID of the session (or null).
                   - "matchedSessionCode": session code (e.g. SES-101).
                   - "matchedDressId": numeric ID of the dress item if known, or null.
                   - "confidence": confidence score percentage from 0 to 100.
                   - "matchReason": clear explanation of why this belongs to this customer/session.
                   - "alternativeMatches": array of strings listing any secondary possible candidate customers or notes.

                $catalogBuilder
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray()
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray()

                    // Text prompt part
                    partsArray.put(JSONObject().apply {
                        put("text", prompt)
                    })

                    // Image part
                    partsArray.put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", queryBase64)
                        })
                    })

                    put("parts", partsArray)
                }
                contentsArray.put(contentObj)
                put("contents", contentsArray)

                // Enforce JSON output format
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                })
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                return@withContext Result.failure(Exception("Gemini API error (${response.code}): $errorBody"))
            }

            val responseBody = response.body?.string().orEmpty()
            val parsedJson = JSONObject(responseBody)
            val candidates = parsedJson.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text").orEmpty()

            if (text.isBlank()) {
                return@withContext Result.failure(Exception("Received empty analysis from Gemini."))
            }

            val analysisJson = JSONObject(text)
            val result = AiDressAnalysisResult(
                matchedCustomerId = if (analysisJson.isNull("matchedCustomerId")) null else analysisJson.optLong("matchedCustomerId"),
                matchedCustomerName = analysisJson.optString("matchedCustomerName", "Unknown Customer"),
                matchedCustomerPhone = analysisJson.optString("matchedCustomerPhone", ""),
                matchedSessionId = if (analysisJson.isNull("matchedSessionId")) null else analysisJson.optLong("matchedSessionId"),
                matchedSessionCode = analysisJson.optString("matchedSessionCode", ""),
                matchedDressId = if (analysisJson.isNull("matchedDressId")) null else analysisJson.optLong("matchedDressId"),
                confidence = analysisJson.optInt("confidence", 75),
                garmentDescription = analysisJson.optString("garmentDescription", "Identified garment"),
                matchReason = analysisJson.optString("matchReason", "Visual and record matching completed."),
                alternativeMatches = buildList {
                    val altArray = analysisJson.optJSONArray("alternativeMatches")
                    if (altArray != null) {
                        for (i in 0 until altArray.length()) {
                            add(altArray.optString(i))
                        }
                    }
                }
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
