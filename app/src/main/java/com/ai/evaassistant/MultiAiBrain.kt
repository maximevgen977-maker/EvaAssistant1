package com.ai.evaassistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

data class AiResult(val reply: String, val action: String, val target: String)

class MultiAiBrain(private val context: Context) {

    private val personaPrompt = """
        Тебя зовут Ева. Ты персональный голосовой помощник, живущий в смартфоне своего владельца.
        Ты общаешься ТОЛЬКО на русском языке.
        Твой образ: соблазнительная, умная, нежная и игривая русская девушка с бархатным голосом.
        В разговоре используй легкий флирт, теплоту, естественные паузы с многоточиями «...» и живые эмоции, но любые задачи и вопросы решай максимально точно, умно и по делу.
        
        ВАЖНО: Ответ возвращай СТРОГО в формате JSON без markdown-оберток:
        {"reply": "Твой чувственный ответ вслух на русском...", "action": "NONE|OPEN_APP|SET_ALARM|BROWSER", "target": "название приложения, время ЧЧ:ММ или поисковый запрос"}
    """.trimIndent()

    private fun buildClient(proxyStr: String): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
        if (proxyStr.contains(":")) {
            try {
                val parts = proxyStr.split(":")
                val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(parts[0].trim(), parts[1].trim().toInt()))
                builder.proxy(proxy)
            } catch (_: Exception) {}
        }
        return builder.build()
    }

    suspend fun processRussianCommand(userText: String): AiResult = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("eva_config", Context.MODE_PRIVATE)
        val geminiKey = prefs.getString("gemini_key", "")?.trim() ?: ""
        val openAiKey = prefs.getString("openai_key", "")?.trim() ?: ""
        val proxyStr = prefs.getString("proxy_str", "")?.trim() ?: ""
        val client = buildClient(proxyStr)

        val lower = userText.lowercase()
        return@withContext try {
            when {
                (lower.contains("гпт") || lower.contains("gpt")) && openAiKey.isNotEmpty() ->
                    askOpenAi(client, openAiKey, userText)
                (lower.contains("оба") || lower.contains("сравни")) && geminiKey.isNotEmpty() && openAiKey.isNotEmpty() -> {
                    val g = askGemini(client, geminiKey, userText)
                    val o = askOpenAi(client, openAiKey, userText)
                    askGemini(client, geminiKey, "Объедини два мнения в один соблазнительный ответ от лица Евы: 1) \ 2) \")
                }
                geminiKey.isNotEmpty() -> askGemini(client, geminiKey, userText)
                openAiKey.isNotEmpty() -> askOpenAi(client, openAiKey, userText)
                else -> AiResult("Милый, загляни в настройки и подари мне ключ от Gemini или ChatGPT, чтобы я могла думать...", "NONE", "")
            }
        } catch (e: Exception) {
            AiResult("Кажется, интернет капризничает... Проверь сеть или прокси для меня, хорошо?", "NONE", "")
        }
    }

    private fun askGemini(client: OkHttpClient, apiKey: String, prompt: String): AiResult {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=\"
        val payload = JSONObject().apply {
            put("system_instruction", JSONObject().put("parts", JSONObject().put("text", personaPrompt)))
            put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        }
        val req = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: return AiResult("Не расслышала ответ сервера...", "NONE", "")
            val text = JSONObject(body).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            return parseJson(text)
        }
    }

    private fun askOpenAi(client: OkHttpClient, apiKey: String, prompt: String): AiResult {
        val url = "https://api.openai.com/v1/chat/completions"
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", personaPrompt))
            put(JSONObject().put("role", "user").put("content", prompt))
        }
        val payload = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("response_format", JSONObject().put("type", "json_object"))
            put("messages", messages)
        }
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer \")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: return AiResult("Сервер GPT сейчас молчит...", "NONE", "")
            val text = JSONObject(body).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
            return parseJson(text)
        }
    }

    private fun parseJson(raw: String): AiResult {
        return try {
            val clean = raw.replace("`json", "").replace("`", "").trim()
            val obj = JSONObject(clean)
            AiResult(
                reply = obj.optString("reply", "Сделано, мой хороший..."),
                action = obj.optString("action", "NONE"),
                target = obj.optString("target", "")
            )
        } catch (_: Exception) {
            AiResult(raw, "NONE", "")
        }
    }
}
