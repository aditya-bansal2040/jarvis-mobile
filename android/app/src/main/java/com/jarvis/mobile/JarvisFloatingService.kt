package com.jarvis.mobile

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.*
import android.widget.Button
import androidx.core.app.NotificationCompat
import java.util.*

class JarvisFloatingService : Service(), TextToSpeech.OnInitListener {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val CHANNEL_ID = "jarvis_service_channel"
    private val NOTIFICATION_ID = 101

    companion object {
        const val ACTION_START_FLOATING = "ACTION_START_FLOATING"
        const val ACTION_STOP_FLOATING = "ACTION_STOP_FLOATING"
        const val ACTION_TRIGGER_VOICE = "ACTION_TRIGGER_VOICE"
    }

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_FLOATING -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_VOICE -> {
                startListening()
                return START_STICKY
            }
        }

        // Start Foreground Notification with Stop & Talk actions
        val notification = buildForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Show Draggable Floating Hologram Overlay Window
        if (floatingView == null) {
            initFloatingOverlay()
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "J.A.R.V.I.S Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Jarvis running in the background with floating hologram controls"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, JarvisFloatingService::class.java).apply {
            action = ACTION_STOP_FLOATING
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val talkIntent = Intent(this, JarvisFloatingService::class.java).apply {
            action = ACTION_TRIGGER_VOICE
        }
        val pendingTalk = PendingIntent.getService(
            this, 2, talkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("J.A.R.V.I.S. Active")
            .setContentText("Hologram overlay active. Tap to interact or stop.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingOpenApp)
            .addAction(android.R.drawable.ic_btn_speak_now, "Talk", pendingTalk)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Jarvis", pendingStop)
            .setOngoing(true)
            .build()
    }

    private fun initFloatingOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.floating_hologram_layout, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        windowManager?.addView(floatingView, params)

        val btnTalk = floatingView?.findViewById<Button>(R.id.btnFloatingTalk)
        val btnStop = floatingView?.findViewById<Button>(R.id.btnFloatingStop)
        val btnExpand = floatingView?.findViewById<Button>(R.id.btnFloatingExpand)

        btnTalk?.setOnClickListener {
            startListening()
        }

        btnStop?.setOnClickListener {
            speak("Jarvis standing down, sir.")
            stopSelf()
        }

        btnExpand?.setOnClickListener {
            val appIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(appIntent)
        }

        // Draggable touch handler
        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("Voice recognition is unavailable on this device, sir.")
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        speak("I could not catch that, sir.")
                    }
                    override fun onResults(results: android.os.Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val userQuery = matches[0]
                            handleQueryWithGemini(userQuery)
                        }
                    }
                    override fun onPartialResults(partialResults: android.os.Bundle?) {}
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                })
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        speechRecognizer?.startListening(intent)
        speak("Listening, sir.")
    }

    private fun handleQueryWithGemini(query: String) {
        val prefs = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""

        if (apiKey.isBlank()) {
            speak("Sir, please open the Jarvis app and save your Gemini API key.")
            return
        }

        GeminiClient.generateReply(
            apiKey = apiKey,
            userPrompt = query,
            onResult = { reply ->
                speak(reply)
            },
            onError = { err ->
                speak("I encountered an issue connecting to Gemini, sir.")
            }
        )
    }

    private fun speak(text: String) {
        if (isTtsReady && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_tts_id")
        }
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
        if (floatingView != null && windowManager != null) {
            windowManager?.removeView(floatingView)
            floatingView = null
        }
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
