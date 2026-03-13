package com.voxshield.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Displays the detection history stored in SharedPreferences.
 *
 * Each entry format: "timestamp|phoneNumber|fakeProbability|riskLevel"
 */
class DetectionHistoryActivity : AppCompatActivity() {

    data class Detection(
        val timestamp: Long,
        val phoneNumber: String,
        val fakeProbability: Float,
        val riskLevel: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detection_history)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val tvNoHistory = findViewById<TextView>(R.id.tvNoHistory)
        val rvHistory = findViewById<RecyclerView>(R.id.rvHistory)

        btnBack.setOnClickListener { finish() }

        val detections = loadHistory()

        if (detections.isEmpty()) {
            tvNoHistory.visibility = View.VISIBLE
            rvHistory.visibility = View.GONE
        } else {
            tvNoHistory.visibility = View.GONE
            rvHistory.visibility = View.VISIBLE
            rvHistory.layoutManager = LinearLayoutManager(this)
            rvHistory.adapter = DetectionAdapter(detections)
        }
    }

    private fun loadHistory(): List<Detection> {
        val prefs = getSharedPreferences("voxshield_history", MODE_PRIVATE)
        val entries = prefs.getStringSet("entries", emptySet()) ?: emptySet()

        return entries.mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size >= 4) {
                Detection(
                    timestamp = parts[0].toLongOrNull() ?: 0L,
                    phoneNumber = parts[1],
                    fakeProbability = parts[2].toFloatOrNull() ?: 0f,
                    riskLevel = parts[3]
                )
            } else null
        }.sortedByDescending { it.timestamp }
    }

    // ---------------------------------------------------------------
    // Adapter
    // ---------------------------------------------------------------

    inner class DetectionAdapter(private val items: List<Detection>) :
        RecyclerView.Adapter<DetectionAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val riskIndicator: View = view.findViewById(R.id.riskIndicator)
            val tvRisk: TextView = view.findViewById(R.id.tvHistoryRisk)
            val tvPhone: TextView = view.findViewById(R.id.tvHistoryPhone)
            val tvTime: TextView = view.findViewById(R.id.tvHistoryTime)
            val tvProb: TextView = view.findViewById(R.id.tvHistoryProb)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_detection, parent, false)
            return VH(view)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val detection = items[position]
            val dateFormat = SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault())

            holder.tvRisk.text = detection.riskLevel
            holder.tvPhone.text = detection.phoneNumber
            holder.tvTime.text = dateFormat.format(Date(detection.timestamp))
            holder.tvProb.text = "${(detection.fakeProbability * 100).toInt()}%"

            val color = when (detection.riskLevel) {
                "SAFE" -> R.color.safe
                "SUSPICIOUS" -> R.color.warning
                "DEEPFAKE" -> R.color.danger
                else -> R.color.text_muted
            }
            holder.riskIndicator.setBackgroundColor(
                ContextCompat.getColor(this@DetectionHistoryActivity, color)
            )
            holder.tvRisk.setTextColor(
                ContextCompat.getColor(this@DetectionHistoryActivity, color)
            )
            holder.tvProb.setTextColor(
                ContextCompat.getColor(this@DetectionHistoryActivity, color)
            )
        }
    }
}
