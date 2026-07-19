# CodeXEmbed Service - Phase 2.5

## Overview
Code embedding service for semantic code similarity detection. Uses CodeXEmbed (NeurIPS 2025) to generate 768-dimensional embeddings for source code.

## Technology Stack
- **Framework**: FastAPI
- **Model**: nomic-ai/nomic-embed-code-v1 (768 dimensions)
- **Language**: Python 3.12
- **Port**: 8005

## Features
- Multi-language code embedding (Python, JavaScript, Java, C++, etc.)
- Batch processing support
- Code similarity computation
- File upload support

## API Endpoints

### Health Check
```
GET /health
```

### Embed Code
```
POST /embed
Body: {
  "code": "def hello():\n    print('Hello')",
  "language": "python"
}
Response: {
  "embedding": [0.123, ...],
  "dimensions": 768,
  "model": "nomic-ai/nomic-embed-code-v1"
}
```

### Batch Embed
```
POST /embed/batch
Body: [
  {"code": "...", "language": "python"},
  {"code": "...", "language": "javascript"}
]
```

### Similarity
```
POST /similarity
Body: {
  "code1": "...",
  "code2": "...",
  "language": "python"
}
Response: {
  "similarity": 0.85,
  "model": "nomic-ai/nomic-embed-code-v1"
}
```

### Embed File
```
POST /embed/file
FormData: file=<code_file>, language=python
```

## Installation
```bash
pip install -r requirements.txt
```

## Run Service
```bash
python main.py
# or
uvicorn main:app --host 0.0.0.0 --port 8005
```

## TDD v5 Compliance
- ✅ Phase 2.5 implementation
- ✅ Layer 4 (L4) code embedding
- ✅ 768-dimensional embeddings
- ✅ Multi-language support
