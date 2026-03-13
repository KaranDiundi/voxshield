"""
VoxShield – Audio Processing Module
Converts raw audio into a 128x128 mel spectrogram tensor for CNN inference.
"""

import numpy as np
import librosa
import cv2
import logging

logger = logging.getLogger("voxshield")


def process_audio(file_path: str) -> np.ndarray:
    """
    Load an audio file and convert it into a normalised 128×128 mel-spectrogram
    tensor ready for the deepfake-voice CNN.

    Parameters
    ----------
    file_path : str
        Path to the audio file (.wav, .mp3, .ogg, etc.).

    Returns
    -------
    np.ndarray
        Tensor of shape (1, 128, 128, 1) with values in [0, 1].
    """

    # 1. Load audio at 16 kHz mono
    y, sr = librosa.load(file_path, sr=16000)

    # Guard against silent / empty clips
    if len(y) == 0:
        raise ValueError("Audio file is empty or could not be decoded.")

    # 2. Compute mel spectrogram (128 mel bands)
    mel_spec = librosa.feature.melspectrogram(
        y=y,
        sr=sr,
        n_mels=128,
        n_fft=2048,
        hop_length=512,
    )

    # 3. Convert power spectrogram to decibel scale
    mel_spec_db = librosa.power_to_db(mel_spec, ref=np.max)

    # 4. Resize to 128×128 using OpenCV
    mel_spec_resized = cv2.resize(mel_spec_db, (128, 128))

    # 5. Normalise to [0, 1]
    min_val = mel_spec_resized.min()
    max_val = mel_spec_resized.max()
    if max_val - min_val > 0:
        mel_spec_norm = (mel_spec_resized - min_val) / (max_val - min_val)
    else:
        mel_spec_norm = np.zeros_like(mel_spec_resized)

    # 6. Reshape to (1, 128, 128, 1) – batch × height × width × channels
    tensor = mel_spec_norm.reshape(1, 128, 128, 1).astype(np.float32)

    logger.info(
        "Processed audio %s → tensor shape %s, range [%.4f, %.4f]",
        file_path,
        tensor.shape,
        tensor.min(),
        tensor.max(),
    )

    return tensor
