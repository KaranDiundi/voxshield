package com.voxshield.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Floating overlay service that displays real-time deepfake detection
 * results during phone calls.
 *
 * Colors:
 *   Green  → SAFE
 *   Yellow → SUSPICIOUS
 *   Red    → DEEPFAKE
 */
class ResultOverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        const val EXTRA_STATUS = "status"      // "listening" | "result"
        const val EXTRA_FAKE_PROB = "fake_prob"
        const val EXTRA_RISK = "risk_level"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isViewAdded = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val status = intent?.getStringExtra(EXTRA_STATUS) ?: "listening"

        when (status) {
            "listening" -> showListening()
            "result" -> {
                val fakeProb = intent?.getFloatExtra(EXTRA_FAKE_PROB, 0f) ?: 0f
                val risk = intent?.getStringExtra(EXTRA_RISK) ?: "SAFE"
                showResult(fakeProb, risk)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    // ---------------------------------------------------------------
    // Overlay management
    // ---------------------------------------------------------------

    private fun getLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100
        }
    }

    private fun createOverlayView(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
            elevation = 8f
        }

        val icon = TextView(this).apply {
            tag = "icon"
            textSize = 32f
            gravity = android.view.Gravity.CENTER
        }

        val title = TextView(this).apply {
            tag = "title"
            textSize = 18f
            setTextColor(0xFFF1F5F9.toInt())
            gravity = android.view.Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val subtitle = TextView(this).apply {
            tag = "subtitle"
            textSize = 14f
            setTextColor(0xFF94A3B8.toInt())
            gravity = android.view.Gravity.CENTER
        }

        container.addView(icon)
        container.addView(title)
        container.addView(subtitle)
        return container
    }

    private fun showOverlay(view: View) {
        if (isViewAdded) {
            windowManager?.removeView(overlayView)
        }
        overlayView = view
        windowManager?.addView(overlayView, getLayoutParams())
        isViewAdded = true
    }

    private fun removeOverlay() {
        if (isViewAdded && overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay", e)
            }
            isViewAdded = false
            overlayView = null
        }
    }

    // ---------------------------------------------------------------
    // Display states
    // ---------------------------------------------------------------

    private fun showListening() {
        val view = createOverlayView()
        val container = view as LinearLayout
        container.setBackgroundColor(0xE61E293B.toInt()) // semi-transparent dark

        val icon = container.findViewWithTag<TextView>("icon")
        val title = container.findViewWithTag<TextView>("title")
        val subtitle = container.findViewWithTag<TextView>("subtitle")

        icon.text = "🎧"
        title.text = "VoxShield Active"
        title.setTextColor(0xFF3B82F6.toInt())
        subtitle.text = "Listening for deepfake patterns…"

        showOverlay(view)
    }

    private fun showResult(fakeProb: Float, riskLevel: String) {
        val view = createOverlayView()
        val container = view as LinearLayout

        val icon = container.findViewWithTag<TextView>("icon")
        val title = container.findViewWithTag<TextView>("title")
        val subtitle = container.findViewWithTag<TextView>("subtitle")

        val confidence = (maxOf(fakeProb, 1f - fakeProb) * 100).toInt()

        when (riskLevel) {
            "SAFE" -> {
                container.setBackgroundColor(0xE614532D.toInt())
                icon.text = "✅"
                title.text = "Human voice likely"
                title.setTextColor(0xFF22C55E.toInt())
                subtitle.text = "Confidence: $confidence%"
            }
            "SUSPICIOUS" -> {
                container.setBackgroundColor(0xE678350F.toInt())
                icon.text = "⚠️"
                title.text = "Suspicious voice pattern"
                title.setTextColor(0xFFF59E0B.toInt())
                subtitle.text = "Confidence: $confidence%"
            }
            "DEEPFAKE" -> {
                container.setBackgroundColor(0xE67F1D1D.toInt())
                icon.text = "🚨"
                title.text = "⚠ Deepfake Voice Detected"
                title.setTextColor(0xFFEF4444.toInt())
                subtitle.text = "AI Probability: ${(fakeProb * 100).toInt()}%"
            }
        }

        showOverlay(view)
    }
}
