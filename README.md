# VoxShield – Deepfake Voice Scam Detector

VoxShield is an end-to-end cybersecurity solution designed to protect users from AI-generated deepfake voice scams. The system consists of a Python FastAPI backend leveraging a TensorFlow CNN model for real-time inference, and an Android application for live call monitoring and overlay alerts.

## 🚀 Features

- **Real-time Call Detection (Android):** Monitors incoming and outgoing calls and analyzes audio in real-time.
- **Deepfake Inference:** Uses a 128x128 Mel Spectrogram-based CNN to determine if a voice is real or AI-generated.
- **Live Overlays:** Displays immediate "SAFE," "SUSPICIOUS," or "DEEPFAKE DETECTED" alerts directly on the call screen.
- **Web App (PWA):** A premium, cybersecurity-themed web interface for manual file uploads and microphone recording.
- **Detection History:** Stores past results for review and logging.

## 📂 Project Structure

```text
voxshield/
├── backend/            # FastAPI Server & ML Model
│   ├── static/         # Web App Frontend (HTML/CSS/JS)
│   ├── server.py       # Main API entry point
│   ├── model_utils.py  # Audio preprocessing logic
│   └── requirements.txt
├── mobile_app/         # Android App Source (Kotlin)
│   ├── app/            # Android app module
│   └── build.gradle    # Root build configuration
└── README.md
```

## 🛠️ Setup & Installation

### 1. Backend (FastAPI)

Prerequisites: Python 3.9+

```bash
cd backend
pip install -r requirements.txt
python server.py
```

The server will start at `http://localhost:8000`. You can access the **Interactive API Docs** at `http://localhost:8000/docs` or the **Web Dashboard** at `http://localhost:8000/`.

### 2. Mobile App (Android)

Prerequisites: Android Studio Jellyfish+

1. Open the `mobile_app` folder in Android Studio.
2. Update the `SERVER_URL` in `ApiClient.kt` if deploying to a physical device (use your laptop's local IP).
3. Build and run on an Android device (API 24+).
4. Grant the necessary permissions: **Record Audio**, **Phone State**, and **Appear on Top**.

## 🧠 Model Information

The core of VoxShield is a TensorFlow CNN model trained to distinguish between synthetic and organic voice patterns.
- **Input:** 128x128 Mel Spectrogram.
- **Architecture:** Multiple Convolutional layers for feature extraction followed by Dense layers for binary classification.
- **Accuracy:** Optimized for low latency and high precision in voice scam detection scenarios.

## 🛡️ License

This project is for educational and cybersecurity research purposes.
