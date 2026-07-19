# VideoMAE Service - Phase 3.0

## Overview
Video temporal embedding service for video similarity and manipulation detection. Uses VideoMAE v2 to generate 768-dimensional embeddings from video frames.

## Technology Stack
- **Framework**: FastAPI
- **Model**: MCG-NJU/videomae-base-finetuned-kinetics (768 dimensions)
- **Language**: Python 3.12
- **Port**: 8006

## Features
- Video temporal embedding (16 frames)
- Video similarity computation
- Temporal manipulation detection (re-cut, re-timing, re-ordering)
- Frame difference analysis

## API Endpoints

### Health Check
```
GET /health
```

### Embed Video
```
POST /embed
FormData: file=<video_file>
Response: {
  "embedding": [0.123, ...],
  "dimensions": 768,
  "num_frames": 16,
  "duration_seconds": 5.2,
  "model": "MCG-NJU/videomae-base-finetuned-kinetics"
}
```

### Similarity
```
POST /similarity
FormData: file1=<video1>, file2=<video2>
Response: {
  "similarity": 0.85,
  "video1_frames": 16,
  "video1_duration": 5.2,
  "video2_frames": 16,
  "video2_duration": 4.8,
  "model": "MCG-NJU/videomae-base-finetuned-kinetics"
}
```

### Temporal Analysis
```
POST /temporal-analysis
FormData: file=<video_file>
Response: {
  "num_frames": 16,
  "duration_seconds": 5.2,
  "avg_frame_difference": 12.5,
  "std_frame_difference": 3.2,
  "max_frame_difference": 45.8,
  "min_frame_difference": 2.1,
  "anomalies_detected": 2,
  "anomalies": [
    {"frame_index": 5, "difference": 45.8, "severity": "high"},
    {"frame_index": 12, "difference": 38.2, "severity": "medium"}
  ],
  "potential_manipulation": false,
  "analysis": "Video appears temporally consistent"
}
```

## Installation
```bash
pip install -r requirements.txt
```

## Run Service
```bash
python main.py
# or
uvicorn main:app --host 0.0.0.0 --port 8006
```

## TDD v5 Compliance
- ✅ Phase 3.0 implementation
- ✅ Layer 7 (L7) video temporal embedding
- ✅ 768-dimensional embeddings
- ✅ Temporal manipulation detection
- ✅ Re-cut/re-timing/re-ordering detection
