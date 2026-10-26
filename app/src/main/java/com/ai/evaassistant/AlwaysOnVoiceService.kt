package com.ai.evaassistant

import android.app.*
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.AlarmClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.Locale

class AlwaysOnVoiceService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var recognizer: SpeechRecognizer? = null
    private lateinit var speechIntent: Intent
    private lateinit var brain: MultiAiBrain
    private lateinit var voice: SeductiveVoiceEngine
    private var isSpeaking = false

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        brain = MultiAiBrain(this)
        voice = SeductiveVoiceEngine(this)
        setupRussianRecognizer()

        // Приветствие при запуске
        isSpeaking = true
        scope.launch {
            voice.speak("Привет, мой хороший... Я на связи и слушаю каждое твое слово.") {
                isSpeaking = false
                startListening()
            }
        }
    }

    private fun setupRussianRecognizer() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val phrase = list?.firstOrNull()?.trim() ?: ""
                if (phrase.isNotEmpty() && !isSpeaking) {
                    val lower = phrase.lowercase(Locale("ru", "RU"))
                    // Срабатывает на имя "Ева", "Евочка", "Слушай" или если активирован режим постоянного диалога
                    val prefs = getSharedPreferences("eva_config", MODE_PRIVATE)
                    val alwaysReply = prefs.getBoolean("always_reply", true)
                    if (alwaysReply || lower.contains("ева") || lower.contains("евочка") || lower.contains("помощник")) {
                        handleCommand(phrase)
                        return
                    }
                }
                restartListeningWithDelay(400)
            }

            override fun onError(error: Int) {
                if (!isSpeaking) restartListeningWithDelay(900)
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        if (!isSpeaking) {
            try { recognizer?.startListening(speechIntent) } catch (_: Exception) {}
        }
    }

    private fun restartListeningWithDelay(ms: Long) {
        scope.launch {
            delay(ms)
            startListening()
        }
    }

    private fun handleCommand(spokenText: String) {
        isSpeaking = true
        scope.launch {
            val result = brain.processRussianCommand(spokenText)
            executeSystemAction(result.action, result.target)
            voice.speak(result.reply) {
                isSpeaking = false
                restartListeningWithDelay(300)
            }
        }
    }

    private fun executeSystemAction(action: String, target: String) {
        try {
            when (action.uppercase()) {
                "OPEN_APP" -> {
                    val pm = packageManager
                    val apps = pm.getInstalledApplications(0)
                    val found = apps.firstOrNull {
                        pm.getApplicationLabel(it).toString().contains(target, ignoreCase = true)
                    }
                    found?.let {
                        val launch = pm.getLaunchIntentForPackage(it.packageName)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (launch != null) startActivity(launch)
                    }
                }
                "SET_ALARM" -> {
                    val parts = target.split(":")
                    val hour = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 8
                    val min = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                    val alarm = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, min)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(alarm)
                }
                "BROWSER" -> {
                    val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(target))
                    val browser = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(browser)
                }
            }
        } catch (_: Exception) {}
    }

    private fun startForegroundNotification() {
        val channelId = "eva_assistant_bg"
        val channel = NotificationChannel(channelId, "Голосовой ассистент Ева", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ева активна (RU)")
            .setContentText("Слушаю команды на русском языке в фоне...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        recognizer?.destroy()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
