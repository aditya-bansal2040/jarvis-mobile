package com.jarvis.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object GeminiClient {

    private const val SYSTEM_PROMPT = 
        "You are J.A.R.V.I.S., Tony Stark's personal artificial intelligence operating on the user's mobile phone. " +
        "Respond with high intellect, concise wit, loyalty, and calm British politeness. Address the user respectfully as 'sir'. " +
        "Keep responses brief, natural, and conversational for mobile voice interactions. Understand English, Hindi, and Hinglish."

    fun generateReply(
        apiKey: String,
        model: String = "gemini-2.5-flash",
        userPrompt: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (apiKey.isBlank()) {
            onError("API key is not configured.")
            return
        }

        thread {
            try {
                val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 20000

                // Payload construction
                val rootJson = JSONObject()
                val contentsArray = JSONArray()
                val userContent = JSONObject()
                userContent.put("role", "user")

                val partsArray = JSONArray()
                val textPart = JSONObject()
                textPart.put("text", userPrompt)
                partsArray.put(textPart)
                userContent.put("parts", partsArray)
                contentsArray.put(userContent)
                rootJson.put("contents", contentsArray)

                // System Instruction
                val sysInstruction = JSONObject()
                val sysParts = JSONArray()
                val sysTextPart = JSONObject()
                sysTextPart.put("text", SYSTEM_PROMPT)
                sysParts.put(sysTextPart)
                sysInstruction.put("parts", sysParts)
                rootJson.put("systemInstruction", sysInstruction)

                // Send request
                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(rootJson.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                val stream = if (responseCode == HttpURLConnection.HTTP_OK) {
                    conn.inputStream
                } else {
                    conn.errorStream
                }

                val responseText = BufferedReader(InputStreamReader(stream, "UTF-8")).use { reader ->
                    reader.readText()
                }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseJson = JSONObject(responseText)
                    val candidates = responseJson.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val contentObj = firstCandidate.optJSONObject("content")
                        val contentParts = contentObj?.optJSONArray("parts")
                        if (contentParts != null && contentParts.length() > 0) {
                            val reply = contentParts.getJSONObject(0).optString("text", "Protocol completed, sir.")
                            onResult(reply)
                            return@thread
                        }
                    }
                    onResult("No response text received, sir.")
                } else {
                    val errJson = JSONObject(responseText)
                    val message = errJson.optJSONObject("error")?.optString("message") ?: "HTTP $responseCode"
                    onError("Gemini Core Error: $message")
                }

            } catch (e: Exception) {
                onError("Network connection failure: ${e.localizedMessage}")
            }
        }
    }
}
