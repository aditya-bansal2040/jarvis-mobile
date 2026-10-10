package com.jarvis.mobile

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvStatus: TextView
    private lateinit var tvResponse: TextView
    private lateinit var tvUserTranscript: TextView
    private lateinit var btnTalk: Button
    private lateinit var btnStop: Button
    private lateinit var btnMute: Button
    private lateinit var btnFloat: Button
    private lateinit var btnSettings: Button

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isMuted = false

    private val PREFS_NAME = "jarvis_prefs"
    private val KEY_API = "gemini_api_key"
    private val REQ_PERMISSIONS = 200
    private val REQ_OVERLAY = 201

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initTTS()
        checkAndRequestPermissions()

        val apiKey = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_API, "")
        if (apiKey.isNullOrBlank()) {
            showSettingsDialog()
        }
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvResponse = findViewById(R.id.tvResponse)
        tvUserTranscript = findViewById(R.id.tvUserTranscript)
        btnTalk = findViewById(R.id.btnTalk)
        btnStop = findViewById(R.id.btnStop)
        btnMute = findViewById(R.id.btnMute)
        btnFloat = findViewById(R.id.btnFloat)
        btnSettings = findViewById(R.id.btnSettings)

        btnTalk.setOnClickListener {
            startListening()
        }

        btnStop.setOnClickListener {
            stopAll()
        }

        btnMute.setOnClickListener {
            isMuted = !isMuted
            btnMute.text = if (isMuted) "MUTED" else "VOICE"
            if (isMuted) tts?.stop()
        }

        btnFloat.setOnClickListener {
            startFloatingOverlay()
        }

        btnSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun initTTS() {
        tts = TextToSpeech(this, this)
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMISSIONS)
        }
    }

    private fun startFloatingOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant 'Display over other apps' permission for Hologram overlay", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQ_OVERLAY)
            return
        }

        val serviceIntent = Intent(this, JarvisFloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Minimize app to show floating hologram
        moveTaskToBack(true)
    }

    private fun startListening() {
        val apiKey = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_API, "") ?: ""
        if (apiKey.isBlank()) {
            speak("Sir, please enter your Gemini API key in settings.")
            showSettingsDialog()
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available on device", Toast.LENGTH_SHORT).show()
            return
        }

        tts?.stop()

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        tvStatus.text = "LISTENING..."
                        btnTalk.text = "LISTENING"
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        btnTalk.text = "TALK"
                    }
                    override fun onError(error: Int) {
                        tvStatus.text = "SYSTEM READY"
                        btnTalk.text = "TALK"
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val userText = matches[0]
                            tvUserTranscript.text = "You: \"$userText\""
                            processWithGemini(userText)
                        } else {
                            tvStatus.text = "SYSTEM READY"
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        speechRecognizer?.startListening(intent)
    }

    private fun processWithGemini(prompt: String) {
        val apiKey = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_API, "") ?: ""
        tvStatus.text = "PROCESSING..."
        tvResponse.text = "Processing request through satellite link..."

        GeminiClient.generateReply(
            apiKey = apiKey,
            userPrompt = prompt,
            onResult = { reply ->
                runOnUiThread {
                    tvStatus.text = "VOCALIZING"
                    tvResponse.text = reply
                    speak(reply)
                }
            },
            onError = { error ->
                runOnUiThread {
                    tvStatus.text = "ERROR"
                    tvResponse.text = error
                    speak("An error occurred, sir.")
                }
            }
        )
    }

    private fun speak(text: String) {
        if (isMuted || !isTtsReady || tts == null) return
        val clean = text.replace(Regex("[*_#`]"), "")
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "jarvis_main_tts")
    }

    private fun stopAll() {
        speechRecognizer?.stopListening()
        tts?.stop()
        tvStatus.text = "STANDBY"
        btnTalk.text = "TALK"
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentKey = prefs.getString(KEY_API, "") ?: ""

        val input = EditText(this).apply {
            hint = "AIzaSy..."
            setText(currentKey)
            setPadding(40, 40, 40, 40)
        }

        AlertDialog.Builder(this)
            .setTitle("Gemini API Key")
            .setMessage("Paste your free Gemini API key to activate Jarvis intelligence:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newKey = input.text.toString().trim()
                prefs.edit().putString(KEY_API, newKey).apply()
                Toast.makeText(this, "API Key Saved", Toast.LENGTH_SHORT).show()
                speak("Protocols initialized, sir.")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.UK
            tts?.setPitch(0.9f)
            tts?.setSpeechRate(1.05f)
            isTtsReady = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}
