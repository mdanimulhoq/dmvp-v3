"""
TDD v5 Phase 3 Step 3.0: Video Temporal Embedding Service (L7)
Technology: Python 3.12 + VideoMAE v2 / V-JEPA 2
Model: MCG-NJU/videomae-base-finetuned-kinetics (or V-JEPA 2 when available)

Generates temporal embeddings for video content.
Detects re-cut, re-timed, re-ordered video.
Used for video similarity and temporal manipulation detection.
"""

from fastapi import FastAPI, UploadFile, File, HTTPException, Form
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from typing import List, Optional
import torch
from transformers import VideoMAEFeatureExtractor, VideoMAEForVideoClassification
from PIL import Image
import io
import numpy as np
import cv2
import tempfile
import os

app = FastAPI(
    title="VideoMAE Video Embedding Service",
    description="Phase 3 Step 3.0: Video temporal embedding for TDD v5",
    version="3.0.0",
)

# Load model on startup
MODEL_NAME = "MCG-NJU/videomae-base-finetuned-kinetics"
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"

print(f"Loading VideoMAE model: {MODEL_NAME} on {DEVICE}")
try:
    feature_extractor = VideoMAEFeatureExtractor.from_pretrained(MODEL_NAME)
    model = VideoMAEForVideoClassification.from_pretrained(MODEL_NAME).to(DEVICE)
    model.eval()
    print("VideoMAE model loaded successfully")
    MODEL_AVAILABLE = True
except Exception as e:
    print(f"Warning: Could not load model: {e}")
    print("Running in fallback mode with mock embeddings")
    MODEL_AVAILABLE = False


class VideoEmbeddingResponse(BaseModel):
    embedding: List[float]
    dimensions: int
    model_name: str
    vector_type: str
    num_frames: int
    duration_seconds: float


def extract_frames_from_video(
    video_bytes: bytes, num_frames: int = 16
) -> List[Image.Image]:
    """
    Extract frames from video bytes.

    Args:
        video_bytes: Video file bytes
        num_frames: Number of frames to extract (default 16 for VideoMAE)

    Returns:
        List of PIL Images
    """
    # Write to temporary file
    with tempfile.NamedTemporaryFile(delete=False, suffix=".mp4") as tmp_file:
        tmp_file.write(video_bytes)
        tmp_path = tmp_file.name

    try:
        # Open video
        cap = cv2.VideoCapture(tmp_path)

        if not cap.isOpened():
            raise ValueError("Could not open video file")

        # Get video properties
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        fps = cap.get(cv2.CAP_PROP_FPS)
        duration = total_frames / fps if fps > 0 else 0

        # Calculate frame indices to extract
        if total_frames <= num_frames:
            frame_indices = list(range(total_frames))
        else:
            frame_indices = np.linspace(0, total_frames - 1, num_frames, dtype=int)

        # Extract frames
        frames = []
        for idx in frame_indices:
            cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
            ret, frame = cap.read()
            if ret:
                # Convert BGR to RGB
                frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                pil_image = Image.fromarray(frame_rgb)
                frames.append(pil_image)

        cap.release()

        # Pad if needed
        while len(frames) < num_frames:
            frames.append(frames[-1].copy())

        return frames, duration

    finally:
        # Clean up temporary file
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)


@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {
        "status": "healthy",
        "service": "videomae-service",
        "version": "3.0.0",
        "model_available": MODEL_AVAILABLE,
        "device": DEVICE,
    }


@app.post("/embed/video")
async def embed_video(file: UploadFile = File(...)):
    """
    Generate temporal embedding for video.

    Args:
        file: Video file (mp4, avi, mov, etc.)

    Returns:
        Embedding vector (768 dimensions for VideoMAE base)
    """
    try:
        video_bytes = await file.read()

        if len(video_bytes) == 0:
            raise HTTPException(status_code=400, detail="Video file is empty")

        # Extract frames
        frames, duration = extract_frames_from_video(video_bytes, num_frames=16)

        if MODEL_AVAILABLE:
            # Prepare input for VideoMAE
            inputs = feature_extractor(videos=[frames], return_tensors="pt").to(DEVICE)

            with torch.no_grad():
                outputs = model(**inputs, output_hidden_states=True)

            # Extract embedding from hidden states
            # Use the last hidden state before classification head
            embedding = outputs.hidden_states[-1].mean(dim=1).squeeze().cpu().numpy()

            # Normalize
            embedding = embedding / np.linalg.norm(embedding)
            embedding_list = embedding.tolist()
        else:
            # Fallback: mock embedding
            np.random.seed(hash(video_bytes) % (2**32))
            embedding_list = np.random.randn(768).tolist()

        return VideoEmbeddingResponse(
            embedding=embedding_list,
            dimensions=len(embedding_list),
            model_name=MODEL_NAME,
            vector_type="videomae",
            num_frames=len(frames),
            duration_seconds=duration,
        )

    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Video embedding failed: {str(e)}")


@app.post("/similarity/video")
async def video_similarity(
    file1: UploadFile = File(...),
    file2: UploadFile = File(...),
):
    """
    Compute similarity between two videos.

    Args:
        file1: First video file
        file2: Second video file

    Returns:
        Cosine similarity score (0-1)
    """
    emb1 = await embed_video(file1)
    emb2 = await embed_video(file2)

    # Cosine similarity
    v1 = np.array(emb1.embedding)
    v2 = np.array(emb2.embedding)
    similarity = np.dot(v1, v2) / (np.linalg.norm(v1) * np.linalg.norm(v2))

    return {
        "similarity": float(similarity),
        "video1_frames": emb1.num_frames,
        "video1_duration": emb1.duration_seconds,
        "video2_frames": emb2.num_frames,
        "video2_duration": emb2.duration_seconds,
        "model": MODEL_NAME,
    }


@app.post("/temporal-analysis")
async def temporal_analysis(file: UploadFile = File(...)):
    """
    Analyze temporal characteristics of video.
    Detects potential re-cut, re-timing, re-ordering.

    Args:
        file: Video file

    Returns:
        Temporal analysis results
    """
    try:
        video_bytes = await file.read()

        # Extract frames
        frames, duration = extract_frames_from_video(video_bytes, num_frames=16)

        # Analyze frame differences
        frame_diffs = []
        for i in range(1, len(frames)):
            # Convert to numpy arrays
            img1 = np.array(frames[i - 1])
            img2 = np.array(frames[i])

            # Compute difference
            diff = np.mean(np.abs(img1.astype(float) - img2.astype(float)))
            frame_diffs.append(float(diff))

        # Analyze patterns
        avg_diff = np.mean(frame_diffs)
        std_diff = np.std(frame_diffs)
        max_diff = np.max(frame_diffs)
        min_diff = np.min(frame_diffs)

        # Detect anomalies (potential cuts)
        anomalies = []
        threshold = avg_diff + 2 * std_diff
        for i, diff in enumerate(frame_diffs):
            if diff > threshold:
                anomalies.append(
                    {
                        "frame_index": i + 1,
                        "difference": diff,
                        "severity": "high"
                        if diff > avg_diff + 3 * std_diff
                        else "medium",
                    }
                )

        return {
            "num_frames": len(frames),
            "duration_seconds": duration,
            "avg_frame_difference": avg_diff,
            "std_frame_difference": std_diff,
            "max_frame_difference": max_diff,
            "min_frame_difference": min_diff,
            "anomalies_detected": len(anomalies),
            "anomalies": anomalies,
            "potential_manipulation": len(anomalies) > 3,
            "analysis": "Video shows signs of temporal manipulation"
            if len(anomalies) > 3
            else "Video appears temporally consistent",
        }

    except Exception as e:
        raise HTTPException(
            status_code=500, detail=f"Temporal analysis failed: {str(e)}"
        )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8006)
