package com.ai.evaassistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("eva_config", Context.MODE_PRIVATE)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 70, 50, 50)
            setBackgroundColor(Color.parseColor("#121218"))
        }

        fun label(txt: String) = TextView(this).apply {
            text = txt
            setTextColor(Color.parseColor("#E0E0FF"))
            textSize = 14f
            setPadding(0, 24, 0, 6)
        }

        fun input(hintTxt: String, savedKey: String, defVal: String) = EditText(this).apply {
            hint = hintTxt
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setText(prefs.getString(savedKey, defVal))
        }

        val header = TextView(this).apply {
            text = "Ева AI — Голосовой Помощник (RU)"
            textSize = 22f
            setTextColor(Color.parseColor("#FF6EC7"))
            setPadding(0, 0, 0, 20)
        }

        val geminiBox = input("Вставь Gemini API Key", "gemini_key", "")
        val openAiBox = input("Вставь OpenAI GPT-4o Key", "openai_key", "")
        val elevenBox = input("Вставь ElevenLabs Key (опционально)", "eleven_key", "")
        val voiceIdBox = input("Voice ID (по умолч. чувственный женский)", "voice_id", "EXAVITQu4vr4xnSDxMaL")
        val proxyBox = input("HTTP Прокси IP:PORT (если без VPN)", "proxy_str", "")

        val wakeCheck = CheckBox(this).apply {
            text = "Отвечать на любую фразу (если выкл — только на имя «Ева»)"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("always_reply", true)
        }

        val startBtn = Button(this).apply {
            text = "ЗАПУСТИТЬ ЕВУ В ФОНОВОМ РЕЖИМЕ"
            setBackgroundColor(Color.parseColor("#D81B60"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                prefs.edit()
                    .putString("gemini_key", geminiBox.text.toString())
                    .putString("openai_key", openAiBox.text.toString())
                    .putString("eleven_key", elevenBox.text.toString())
                    .putString("voice_id", voiceIdBox.text.toString())
                    .putString("proxy_str", proxyBox.text.toString())
                    .putBoolean("always_reply", wakeCheck.isChecked)
                    .apply()
                requestAudioAndStart()
            }
        }

        val stopBtn = Button(this).apply {
            text = "Остановить фоновую работу"
            setOnClickListener {
                stopService(Intent(this@MainActivity, AlwaysOnVoiceService::class.java))
                Toast.makeText(this@MainActivity, "Ева отдыхает", Toast.LENGTH_SHORT).show()
            }
        }

        root.addView(header)
        root.addView(label("Ключ Google Gemini (бесплатно в Google AI Studio):"))
        root.addView(geminiBox)
        root.addView(label("Ключ OpenAI (для команд «Ева, спроси у ГПТ»):"))
        root.addView(openAiBox)
        root.addView(label("Ключ ElevenLabs (живой соблазнительный голос):"))
        root.addView(elevenBox)
        root.addView(label("ID женского голоса ElevenLabs:"))
        root.addView(voiceIdBox)
        root.addView(label("Прокси IP:PORT (опционально для работы без VPN):"))
        root.addView(proxyBox)
        root.addView(wakeCheck)
        root.addView(startBtn)
        root.addView(stopBtn)

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun requestAudioAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE), 100)
            return
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:\")))
        }
        val svc = Intent(this, AlwaysOnVoiceService::class.java)
        stopService(svc)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
        Toast.makeText(this, "Ева запущена! Можешь свернуть приложение и говорить по-русски.", Toast.LENGTH_LONG).show()
    }
}
