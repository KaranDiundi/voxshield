package com.voxshield.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

/**
 * Foreground service that detects phone call state changes, records audio in
 * 3-second chunks, sends each chunk to the VoxShield backend for analysis,
 * and triggers overlay alerts with the results.
 */
class CallProtectionService : Service() {

    companion object {
        private const val TAG = "CallProtectionSvc"
        private const val CHANNEL_ID = "voxshield_protection"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_BASE_URL = "base_url"
    }

    private var audioRecorder: AudioRecorder? = null
    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var baseUrl: String = "http://10.0.2.2:8000"
    private var isInCall = false

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        baseUrl = intent?.getStringExtra(EXTRA_BASE_URL) ?: baseUrl
        startPhoneStateListener()
        Log.i(TAG, "Service started – monitoring calls (server: $baseUrl)")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopPhoneStateListener()
        stopCallRecording()
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }

    // ---------------------------------------------------------------
    // Phone state monitoring
    // ---------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun startPhoneStateListener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        Log.i(TAG, "Call active – starting recording")
                        isInCall = true
                        startCallRecording(phoneNumber ?: "Unknown")
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (isInCall) {
                            Log.i(TAG, "Call ended – stopping recording")
                            isInCall = false
                            stopCallRecording()
                        }
                    }
                    TelephonyManager.CALL_STATE_RINGING -> {
                        Log.i(TAG, "Incoming call from ${phoneNumber ?: "Unknown"}")
                    }
                }
            }
        }

        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun stopPhoneStateListener() {
        phoneStateListener?.let {
            telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
        }
        phoneStateListener = null
    }

    // ---------------------------------------------------------------
    // Audio recording & analysis
    // ---------------------------------------------------------------

    private fun startCallRecording(phoneNumber: String) {
        audioRecorder = AudioRecorder(cacheDir)
        audioRecorder?.startRecording { wavFile ->
            analyzeChunk(wavFile, phoneNumber)
        }

        // Show overlay
        val overlayIntent = Intent(this, ResultOverlayService::class.java).apply {
            putExtra(ResultOverlayService.EXTRA_STATUS, "listening")
        }
        startService(overlayIntent)
    }

    private fun stopCallRecording() {
        audioRecorder?.stopRecording()
        audioRecorder = null

        // Remove overlay
        stopService(Intent(this, ResultOverlayService::class.java))
    }

    private fun analyzeChunk(wavFile: File, phoneNumber: String) {
        val api = ApiClient.getApi(baseUrl)
        val requestBody = wavFile.asRequestBody("audio/wav".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", wavFile.name, requestBody)

        api.predict(part).enqueue(object : Callback<PredictionResponse> {
            override fun onResponse(call: Call<PredictionResponse>, response: Response<PredictionResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    Log.i(TAG, "Prediction: fake=${body.fake_probability}  risk=${body.risk_level}")

                    // Update overlay
                    val overlayIntent = Intent(this@CallProtectionService, ResultOverlayService::class.java).apply {
                        putExtra(ResultOverlayService.EXTRA_STATUS, "result")
                        putExtra(ResultOverlayService.EXTRA_FAKE_PROB, body.fake_probability)
                        putExtra(ResultOverlayService.EXTRA_RISK, body.risk_level)
                    }
                    startService(overlayIntent)

                    // Save to detection history
                    saveDetection(phoneNumber, body.fake_probability, body.risk_level)
                } else {
                    Log.e(TAG, "API error: ${response.code()} ${response.message()}")
                }

                // Cleanup temp chunk
                wavFile.delete()
            }

            override fun onFailure(call: Call<PredictionResponse>, t: Throwable) {
                Log.e(TAG, "API call failed", t)
                wavFile.delete()
            }
        })
    }

    private fun saveDetection(phoneNumber: String, fakeProb: Float, riskLevel: String) {
        val prefs = getSharedPreferences("voxshield_history", MODE_PRIVATE)
        val history = prefs.getStringSet("entries", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val entry = "${System.currentTimeMillis()}|$phoneNumber|$fakeProb|$riskLevel"
        history.add(entry)
        prefs.edit().putStringSet("entries", history).apply()
    }

    // ---------------------------------------------------------------
    // Notification
    // ---------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("VoxShield Active")
            .setContentText("Monitoring calls for deepfake voices…")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }
}
