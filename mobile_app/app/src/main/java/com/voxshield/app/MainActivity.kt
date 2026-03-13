package com.voxshield.app

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import android.widget.LinearLayout
import android.widget.TextView
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream

/**
 * VoxShield main screen — cybersecurity-themed dashboard for deepfake voice detection.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val RC_PERMISSIONS = 100
        private const val RC_PICK_AUDIO = 200
    }

    // UI elements
    private lateinit var tvStatus: TextView
    private lateinit var statusDot: View
    private lateinit var statusCard: CardView
    private lateinit var waveformContainer: LinearLayout
    private lateinit var resultCard: CardView
    private lateinit var tvResultIcon: TextView
    private lateinit var tvResultTitle: TextView
    private lateinit var tvResultConfidence: TextView
    private lateinit var tvResultRisk: TextView
    private lateinit var btnCallProtection: MaterialButton
    private lateinit var btnAnalyzeAudio: MaterialButton
    private lateinit var btnViewHistory: MaterialButton
    private lateinit var etServerUrl: TextInputEditText

    private var isProtectionActive = false
    private val waveAnimators = mutableListOf<ObjectAnimator>()

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupListeners()
    }

    override fun onDestroy() {
        stopWaveAnimation()
        super.onDestroy()
    }

    // ---------------------------------------------------------------
    // View binding
    // ---------------------------------------------------------------

    private fun bindViews() {
        tvStatus = findViewById(R.id.tvStatus)
        statusDot = findViewById(R.id.statusDot)
        statusCard = findViewById(R.id.statusCard)
        waveformContainer = findViewById(R.id.waveformContainer)
        resultCard = findViewById(R.id.resultCard)
        tvResultIcon = findViewById(R.id.tvResultIcon)
        tvResultTitle = findViewById(R.id.tvResultTitle)
        tvResultConfidence = findViewById(R.id.tvResultConfidence)
        tvResultRisk = findViewById(R.id.tvResultRisk)
        btnCallProtection = findViewById(R.id.btnCallProtection)
        btnAnalyzeAudio = findViewById(R.id.btnAnalyzeAudio)
        btnViewHistory = findViewById(R.id.btnViewHistory)
        etServerUrl = findViewById(R.id.etServerUrl)
    }

    // ---------------------------------------------------------------
    // Listeners
    // ---------------------------------------------------------------

    private fun setupListeners() {
        btnCallProtection.setOnClickListener { toggleCallProtection() }
        btnAnalyzeAudio.setOnClickListener { pickAudioFile() }
        btnViewHistory.setOnClickListener {
            startActivity(Intent(this, DetectionHistoryActivity::class.java))
        }
    }

    // ---------------------------------------------------------------
    // Call protection toggle
    // ---------------------------------------------------------------

    private fun toggleCallProtection() {
        if (isProtectionActive) {
            stopProtection()
        } else {
            if (checkPermissions()) {
                startProtection()
            }
        }
    }

    private fun startProtection() {
        isProtectionActive = true
        updateStatusUI(true)

        val serviceIntent = Intent(this, CallProtectionService::class.java).apply {
            putExtra(CallProtectionService.EXTRA_BASE_URL, getServerUrl())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        btnCallProtection.text = getString(R.string.btn_stop_protection)
        btnCallProtection.setBackgroundColor(ContextCompat.getColor(this, R.color.danger))
        startWaveAnimation()
        Toast.makeText(this, "Call protection activated", Toast.LENGTH_SHORT).show()
    }

    private fun stopProtection() {
        isProtectionActive = false
        updateStatusUI(false)

        stopService(Intent(this, CallProtectionService::class.java))

        btnCallProtection.text = getString(R.string.btn_call_protection)
        btnCallProtection.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
        stopWaveAnimation()
        Toast.makeText(this, "Call protection deactivated", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatusUI(active: Boolean) {
        if (active) {
            tvStatus.text = getString(R.string.status_active)
            statusDot.setBackgroundResource(R.drawable.status_dot_active)
            waveformContainer.visibility = View.VISIBLE
        } else {
            tvStatus.text = getString(R.string.status_off)
            statusDot.setBackgroundResource(R.drawable.status_dot_off)
            waveformContainer.visibility = View.GONE
        }
    }

    // ---------------------------------------------------------------
    // Waveform animation
    // ---------------------------------------------------------------

    private fun startWaveAnimation() {
        stopWaveAnimation()
        val waveViews = listOf(
            R.id.wave1, R.id.wave2, R.id.wave3, R.id.wave4, R.id.wave5, R.id.wave6,
            R.id.wave7, R.id.wave8, R.id.wave9, R.id.wave10, R.id.wave11, R.id.wave12
        )
        waveViews.forEachIndexed { index, id ->
            val view = findViewById<View>(id)
            val animator = ObjectAnimator.ofFloat(view, "scaleY", 0.3f, 1.0f, 0.3f).apply {
                duration = 800L + (index * 100L)
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                startDelay = index * 50L
            }
            animator.start()
            waveAnimators.add(animator)
        }
    }

    private fun stopWaveAnimation() {
        waveAnimators.forEach { it.cancel() }
        waveAnimators.clear()
    }

    // ---------------------------------------------------------------
    // Audio file analysis
    // ---------------------------------------------------------------

    private fun pickAudioFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "Select audio file"), RC_PICK_AUDIO)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_PICK_AUDIO && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri -> analyzeAudioFile(uri) }
        }
    }

    private fun analyzeAudioFile(uri: Uri) {
        // Show loading state
        resultCard.visibility = View.VISIBLE
        tvResultIcon.text = "⏳"
        tvResultTitle.text = getString(R.string.analyzing)
        tvResultConfidence.text = ""
        tvResultRisk.text = ""

        // Copy to temp file
        val tempFile = File(cacheDir, "upload_${System.currentTimeMillis()}.wav")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy audio file", e)
            showError("Failed to read audio file")
            return
        }

        // Send to API
        val api = ApiClient.getApi(getServerUrl())
        val requestBody = tempFile.asRequestBody("audio/wav".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", tempFile.name, requestBody)

        api.predict(part).enqueue(object : Callback<PredictionResponse> {
            override fun onResponse(call: Call<PredictionResponse>, response: Response<PredictionResponse>) {
                tempFile.delete()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    runOnUiThread { displayResult(body) }
                } else {
                    runOnUiThread { showError("Server error: ${response.code()}") }
                }
            }

            override fun onFailure(call: Call<PredictionResponse>, t: Throwable) {
                tempFile.delete()
                runOnUiThread { showError("Connection failed: ${t.message}") }
            }
        })
    }

    private fun displayResult(result: PredictionResponse) {
        resultCard.visibility = View.VISIBLE
        val confidence = (maxOf(result.fake_probability, result.real_probability) * 100).toInt()

        when (result.risk_level) {
            "SAFE" -> {
                tvResultIcon.text = "✅"
                tvResultTitle.text = getString(R.string.result_safe)
                tvResultTitle.setTextColor(ContextCompat.getColor(this, R.color.safe))
                tvResultConfidence.text = getString(R.string.confidence_label, confidence)
                tvResultRisk.text = "SAFE"
                tvResultRisk.setTextColor(ContextCompat.getColor(this, R.color.safe))
            }
            "SUSPICIOUS" -> {
                tvResultIcon.text = "⚠️"
                tvResultTitle.text = getString(R.string.result_suspicious)
                tvResultTitle.setTextColor(ContextCompat.getColor(this, R.color.warning))
                tvResultConfidence.text = getString(R.string.confidence_label, confidence)
                tvResultRisk.text = "SUSPICIOUS"
                tvResultRisk.setTextColor(ContextCompat.getColor(this, R.color.warning))
            }
            "DEEPFAKE" -> {
                tvResultIcon.text = "🚨"
                tvResultTitle.text = getString(R.string.result_deepfake)
                tvResultTitle.setTextColor(ContextCompat.getColor(this, R.color.danger))
                tvResultConfidence.text = getString(R.string.ai_probability_label, (result.fake_probability * 100).toInt())
                tvResultRisk.text = "DEEPFAKE – HIGH RISK"
                tvResultRisk.setTextColor(ContextCompat.getColor(this, R.color.danger))
            }
        }

        // Save detection
        saveDetectionToHistory("Upload", result.fake_probability, result.risk_level)
    }

    private fun showError(message: String) {
        resultCard.visibility = View.VISIBLE
        tvResultIcon.text = "❌"
        tvResultTitle.text = "Error"
        tvResultTitle.setTextColor(ContextCompat.getColor(this, R.color.danger))
        tvResultConfidence.text = message
        tvResultRisk.text = ""
    }

    private fun saveDetectionToHistory(phoneNumber: String, fakeProb: Float, riskLevel: String) {
        val prefs = getSharedPreferences("voxshield_history", MODE_PRIVATE)
        val history = prefs.getStringSet("entries", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val entry = "${System.currentTimeMillis()}|$phoneNumber|$fakeProb|$riskLevel"
        history.add(entry)
        prefs.edit().putStringSet("entries", history).apply()
    }

    // ---------------------------------------------------------------
    // Server URL
    // ---------------------------------------------------------------

    private fun getServerUrl(): String {
        val url = etServerUrl.text?.toString()?.trim() ?: "http://10.0.2.2:8000"
        return if (url.isBlank()) "http://10.0.2.2:8000" else url
    }

    // ---------------------------------------------------------------
    // Permissions
    // ---------------------------------------------------------------

    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE
        )

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), RC_PERMISSIONS)
            return false
        }

        // Check overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant overlay permission", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return false
        }

        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startProtection()
            } else {
                Toast.makeText(this, "Permissions required for call protection", Toast.LENGTH_LONG).show()
            }
        }
    }
}
