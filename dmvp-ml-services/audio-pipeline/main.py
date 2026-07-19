"""
TDD v5 Phase 2 Step 2.4: Audio Pipeline (L6)
Technology: Python 3.12 + Chromaprint + LAION-CLAP + MERT

Audio fingerprinting and embedding:
- Chromaprint: Acoustic fingerprint (AcoustID/MusicBrainz standard)
- LAION-CLAP v1.3.2: General audio similarity embedding
- MERT v1 (330M): Music-specific embedding

Routing logic:
- Music → MERT
- Speech/environment → CLAP
- General → CLAP
"""

from fastapi import FastAPI, UploadFile, File, HTTPException
from pydantic import BaseModel
from typing import Optional, List
import hashlib
import subprocess
import tempfile
import os
import json

app = FastAPI(title="DMVP Audio Pipeline", version="5.0.0")

# Try to import ML libraries
try:
    import torch
    import laion_clap

    CLAP_AVAILABLE = True
except ImportError:
    CLAP_AVAILABLE = False
    print("Warning: LAION-CLAP not available")

try:
    from transformers import AutoModel, AutoFeatureExtractor

    MERT_AVAILABLE = True
except ImportError:
    MERT_AVAILABLE = False
    print("Warning: MERT not available")


class AudioFingerprint(BaseModel):
    """L6 Audio fingerprint"""

    chromaprint: Optional[str] = None
    clap_embedding: Optional[List[float]] = None
    mert_embedding: Optional[List[float]] = None
    duration_seconds: float
    sample_rate: int
    channels: int
    audio_type: str  # "music", "speech", "environment", "unknown"


class FingerprintResponse(BaseModel):
    success: bool
    fingerprint: Optional[AudioFingerprint] = None
    error: Optional[str] = None
    modality: str = "audio"
    layer: str = "L6"


def compute_chromaprint(file_path: str) -> Optional[str]:
    """
    Compute Chromaprint acoustic fingerprint using fpcalc binary
    AcoustID/MusicBrainz standard
    """
    try:
        result = subprocess.run(
            ["fpcalc", "-raw", "-length", "120", file_path],
            capture_output=True,
            text=True,
            timeout=30,
        )

        if result.returncode == 0:
            # Parse fpcalc output
            for line in result.stdout.split("\n"):
                if line.startswith("FINGERPRINT="):
                    return line.split("=", 1)[1]
        return None
    except (subprocess.TimeoutExpired, FileNotFoundError):
        return None


def get_audio_info(file_path: str) -> dict:
    """Get basic audio file info using ffprobe"""
    try:
        result = subprocess.run(
            [
                "ffprobe",
                "-v",
                "quiet",
                "-print_format",
                "json",
                "-show_format",
                "-show_streams",
                file_path,
            ],
            capture_output=True,
            text=True,
            timeout=10,
        )

        if result.returncode == 0:
            info = json.loads(result.stdout)
            audio_stream = next(
                (s for s in info.get("streams", []) if s.get("codec_type") == "audio"),
                {},
            )
            return {
                "duration": float(info.get("format", {}).get("duration", 0)),
                "sample_rate": int(audio_stream.get("sample_rate", 0)),
                "channels": int(audio_stream.get("channels", 0)),
            }
    except:
        pass

    return {"duration": 0, "sample_rate": 0, "channels": 0}


def classify_audio_type(file_path: str) -> str:
    """
    Simple heuristic to classify audio type for routing
    In production, would use a classifier model
    """
    # For now, use file extension as heuristic
    filename = os.path.basename(file_path).lower()

    if any(ext in filename for ext in [".mp3", ".flac", ".wav", ".m4a"]):
        return "music"
    elif any(ext in filename for ext in [".ogg", ".opus"]):
        return "speech"
    else:
        return "unknown"


def compute_clap_embedding(file_path: str) -> Optional[List[float]]:
    """
    Compute LAION-CLAP v1.3.2 embedding for general audio similarity
    """
    if not CLAP_AVAILABLE:
        return None

    try:
        # Load CLAP model
        model = laion_clap.CLAP_Module(enable_refine=False, amodel="HTSAT-tiny")
        model.load_ckpt()

        # Compute embedding
        embedding = model.get_audio_embed_from_file(file_path)
        return embedding[0].tolist()
    except Exception as e:
        print(f"CLAP embedding failed: {e}")
        return None


def compute_mert_embedding(file_path: str) -> Optional[List[float]]:
    """
    Compute MERT v1 (330M) embedding for music-specific similarity
    """
    if not MERT_AVAILABLE:
        return None

    try:
        # Load MERT model
        model_name = "m-a-p/MERT_v1_330M"
        model = AutoModel.from_pretrained(model_name, trust_remote_code=True)
        feature_extractor = AutoFeatureExtractor.from_pretrained(
            model_name, trust_remote_code=True
        )

        # Load and preprocess audio
        import torchaudio

        waveform, sample_rate = torchaudio.load(file_path)

        # Resample if needed
        if sample_rate != 24000:
            resampler = torchaudio.transforms.Resample(sample_rate, 24000)
            waveform = resampler(waveform)
            sample_rate = 24000

        # Extract features
        inputs = feature_extractor(
            waveform.squeeze().numpy(), sampling_rate=24000, return_tensors="pt"
        )

        # Get embedding
        with torch.no_grad():
            outputs = model(**inputs)
            embedding = outputs.last_hidden_state.mean(dim=1).squeeze()

        return embedding.tolist()
    except Exception as e:
        print(f"MERT embedding failed: {e}")
        return None


@app.post("/fingerprint/audio", response_model=FingerprintResponse)
async def compute_audio_fingerprint(file: UploadFile = File(...)):
    """
    Compute L6 audio fingerprint with routing logic
    """
    # Save uploaded file temporarily
    with tempfile.NamedTemporaryFile(delete=False, suffix=".wav") as tmp:
        content = await file.read()
        tmp.write(content)
        tmp_path = tmp.name

    try:
        # Get audio info
        audio_info = get_audio_info(tmp_path)

        # Compute Chromaprint
        chromaprint = compute_chromaprint(tmp_path)

        # Classify audio type for routing
        audio_type = classify_audio_type(tmp_path)

        # Route to appropriate embedding model
        clap_embedding = None
        mert_embedding = None

        if audio_type == "music":
            # Music → MERT (primary), CLAP (fallback)
            mert_embedding = compute_mert_embedding(tmp_path)
            if mert_embedding is None:
                clap_embedding = compute_clap_embedding(tmp_path)
        else:
            # Speech/environment/general → CLAP
            clap_embedding = compute_clap_embedding(tmp_path)

        fingerprint = AudioFingerprint(
            chromaprint=chromaprint,
            clap_embedding=clap_embedding,
            mert_embedding=mert_embedding,
            duration_seconds=audio_info["duration"],
            sample_rate=audio_info["sample_rate"],
            channels=audio_info["channels"],
            audio_type=audio_type,
        )

        return FingerprintResponse(success=True, fingerprint=fingerprint)

    except Exception as e:
        return FingerprintResponse(success=False, error=str(e))

    finally:
        # Cleanup
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)


@app.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "service": "audio-pipeline",
        "clap_available": CLAP_AVAILABLE,
        "mert_available": MERT_AVAILABLE,
        "version": "5.0.0",
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8003)
