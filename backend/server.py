"""
VoxShield – FastAPI Backend Server
Loads the deepfake-voice CNN model and exposes a /predict endpoint that
accepts audio files and returns fake/real probabilities with risk level.
"""

import os
import uuid
import logging
import tempfile
from contextlib import asynccontextmanager

import numpy as np
import tensorflow as tf
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, FileResponse
from fastapi.staticfiles import StaticFiles

from model_utils import process_audio

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-8s  %(name)s  %(message)s",
)
logger = logging.getLogger("voxshield")

# ---------------------------------------------------------------------------
# Model singleton
# ---------------------------------------------------------------------------
MODEL_PATH = os.path.join(os.path.dirname(__file__), "deepfake_voice.h5")
model: tf.keras.Model | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Load the TF model once on startup."""
    global model
    logger.info("Loading model from %s …", MODEL_PATH)
    model = tf.keras.models.load_model(MODEL_PATH)
    logger.info("Model loaded successfully.  Input shape: %s", model.input_shape)
    yield
    logger.info("Shutting down – releasing model.")
    model = None


# ---------------------------------------------------------------------------
# FastAPI app
# ---------------------------------------------------------------------------
app = FastAPI(
    title="VoxShield API",
    description="Deepfake voice detection – upload audio, get a prediction.",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def _risk_level(fake_prob: float) -> str:
    """Map fake probability to a human-readable risk level."""
    if fake_prob >= 0.7:
        return "DEEPFAKE"
    if fake_prob >= 0.3:
        return "SUSPICIOUS"
    return "SAFE"


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------
@app.get("/")
async def root():
    """Serve the VoxShield Web App UI."""
    return FileResponse(os.path.join(os.path.dirname(__file__), "static", "index.html"))

# Mount static files (manifest, icons, etc.)
app.mount("/", StaticFiles(directory=os.path.join(os.path.dirname(__file__), "static")), name="static")


@app.get("/health")
async def health():
    return {"status": "healthy", "model_loaded": model is not None}


@app.post("/predict")
async def predict(file: UploadFile = File(...)):
    """
    Accept an audio file (WAV, MP3, OGG, etc.), run the deepfake-voice CNN,
    and return probabilities + risk level.
    """
    if model is None:
        raise HTTPException(status_code=503, detail="Model not loaded yet.")

    # Save upload to a temporary file
    suffix = os.path.splitext(file.filename or "audio.wav")[1] or ".wav"
    tmp_path = os.path.join(tempfile.gettempdir(), f"voxshield_{uuid.uuid4().hex}{suffix}")

    try:
        contents = await file.read()
        with open(tmp_path, "wb") as f:
            f.write(contents)
        logger.info("Received file '%s' (%d bytes) → %s", file.filename, len(contents), tmp_path)

        # Process audio → tensor
        tensor = process_audio(tmp_path)

        # Inference
        prediction = model.predict(tensor, verbose=0)  # shape (1, 1) or (1, 2)

        # Handle both single-sigmoid and softmax outputs
        if prediction.shape[-1] == 1:
            fake_prob = float(prediction[0][0])
        else:
            fake_prob = float(prediction[0][1])

        real_prob = round(1.0 - fake_prob, 4)
        fake_prob = round(fake_prob, 4)
        risk = _risk_level(fake_prob)

        logger.info(
            "Prediction → fake=%.4f  real=%.4f  risk=%s",
            fake_prob,
            real_prob,
            risk,
        )

        return JSONResponse(
            content={
                "fake_probability": fake_prob,
                "real_probability": real_prob,
                "risk_level": risk,
            }
        )

    except ValueError as exc:
        logger.error("Audio processing error: %s", exc)
        raise HTTPException(status_code=400, detail=str(exc))
    except Exception as exc:
        logger.exception("Unexpected error during prediction")
        raise HTTPException(status_code=500, detail="Internal prediction error.")
    finally:
        # Clean up temp file
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


# ---------------------------------------------------------------------------
# Direct run support
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    import uvicorn

    uvicorn.run("server:app", host="0.0.0.0", port=8000, reload=True)
