package com.ai.evaassistant

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class SeductiveVoiceEngine(private val context: Context) {

    private val client = OkHttpClient()
    private var mediaPlayer: MediaPlayer? = null
    private var fallbackTts: TextToSpeech? = null
    private var ttsReady = false

    init {
        fallbackTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                fallbackTts?.language = Locale("ru", "RU")
                // Настраиваем мягкий, чуть замедленный и бархатный тембр для системного резерва
                fallbackTts?.setPitch(1.12f)
                fallbackTts?.setSpeechRate(0.92f)
                ttsReady = true
            }
        }
    }

    suspend fun speak(text: String, onComplete: () -> Unit) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("eva_config", Context.MODE_PRIVATE)
        val elevenKey = prefs.getString("eleven_key", "")?.trim() ?: ""
        val voiceId = prefs.getString("voice_id", "EXAVITQu4vr4xnSDxMaL")?.trim().takeUnless { it.isNullOrEmpty() }
            ?: "EXAVITQu4vr4xnSDxMaL"

        if (elevenKey.isNotEmpty()) {
            val played = playElevenLabs(text, elevenKey, voiceId, onComplete)
            if (played) return@withContext
        }

        // Если ключ ElevenLabs еще не введен или нет сети — говорим нежным системным русским голосом
        withContext(Dispatchers.Main) {
            if (ttsReady && fallbackTts != null) {
                fallbackTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { onComplete() }
                    override fun onError(utteranceId: String?) { onComplete() }
                })
                val params = Bundle()
                fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "EVA_UTTERANCE")
            } else {
                onComplete()
            }
        }
    }

    private suspend fun playElevenLabs(
        text: String,
        apiKey: String,
        voiceId: String,
        onComplete: () -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "https://api.elevenlabs.io/v1/text-to-speech/\/stream"
            val settings = JSONObject().apply {
                put("stability", 0.35)
                put("similarity_boost", 0.85)
                put("style", 0.65)
                put("use_speaker_boost", true)
            }
            val payload = JSONObject().apply {
                put("text", text)
                put("model_id", "eleven_multilingual_v2")
                put("voice_settings", settings)
            }
            val req = Request.Builder()
                .url(url)
                .addHeader("xi-api-key", apiKey)
                .addHeader("Accept", "audio/mpeg")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val file = File(context.cacheDir, "eva_voice.mp3")
                FileOutputStream(file).use { out -> resp.body?.byteStream()?.copyTo(out) }

                withContext(Dispatchers.Main) {
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        prepare()
                        setOnCompletionListener { onComplete() }
                        start()
                    }
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
