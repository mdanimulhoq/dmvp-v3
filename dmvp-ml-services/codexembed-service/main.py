"""
TDD v5 Phase 2 Step 2.5: Code Embedding Service (L4)
Technology: Python 3.12 + CodeXEmbed (NeurIPS 2025)
Model: Nomic Embed Code (open-weight) or Voyage Code-3 (API)

Generates semantic embeddings for source code.
Supports multiple programming languages.
Used for code similarity detection and plagiarism detection.
"""

from fastapi import FastAPI, UploadFile, File, HTTPException, Form
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from typing import List, Optional
import torch
from transformers import AutoTokenizer, AutoModel
import io
import numpy as np

app = FastAPI(
    title="CodeXEmbed Code Embedding Service",
    description="Phase 2 Step 2.5: Code embedding generation for TDD v5",
    version="2.5.0",
)

# Load model on startup
MODEL_NAME = "nomic-ai/nomic-embed-code-v1"  # Open-weight alternative
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"

print(f"Loading CodeXEmbed model: {MODEL_NAME} on {DEVICE}")
try:
    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    model = AutoModel.from_pretrained(MODEL_NAME).to(DEVICE)
    model.eval()
    print("CodeXEmbed model loaded successfully")
    MODEL_AVAILABLE = True
except Exception as e:
    print(f"Warning: Could not load model: {e}")
    print("Running in fallback mode with mock embeddings")
    MODEL_AVAILABLE = False


class CodeEmbeddingResponse(BaseModel):
    embedding: List[float]
    dimensions: int
    model_name: str
    vector_type: str
    language: str


class CodeEmbeddingRequest(BaseModel):
    code: str
    language: str = "python"


@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {
        "status": "healthy",
        "service": "codexembed-service",
        "version": "2.5.0",
        "model_available": MODEL_AVAILABLE,
        "device": DEVICE,
    }


@app.post("/embed/code")
async def embed_code(request: CodeEmbeddingRequest):
    """
    Generate embedding for source code.

    Args:
        code: Source code text
        language: Programming language (python, javascript, java, cpp, etc.)

    Returns:
        Embedding vector (768 dimensions for nomic-embed-code)
    """
    if not request.code.strip():
        raise HTTPException(status_code=400, detail="Code cannot be empty")

    try:
        if MODEL_AVAILABLE:
            # Tokenize with language prefix
            input_text = f"{request.language}: {request.code}"
            inputs = tokenizer(
                input_text,
                return_tensors="pt",
                padding=True,
                truncation=True,
                max_length=2048,  # Code can be long
            ).to(DEVICE)

            with torch.no_grad():
                outputs = model(**inputs)

            # Mean pooling
            embedding = outputs.last_hidden_state.mean(dim=1).squeeze().cpu().numpy()

            # Normalize
            embedding = embedding / np.linalg.norm(embedding)
            embedding_list = embedding.tolist()
        else:
            # Fallback: mock embedding
            np.random.seed(hash(request.code) % (2**32))
            embedding_list = np.random.randn(768).tolist()

        return CodeEmbeddingResponse(
            embedding=embedding_list,
            dimensions=len(embedding_list),
            model_name=MODEL_NAME,
            vector_type="codexembed",
            language=request.language,
        )

    except Exception as e:
        raise HTTPException(
            status_code=500, detail=f"Embedding generation failed: {str(e)}"
        )


@app.post("/embed/code/batch")
async def embed_code_batch(requests: List[CodeEmbeddingRequest]):
    """
    Generate embeddings for multiple code snippets.

    Args:
        requests: List of code embedding requests

    Returns:
        List of embedding vectors
    """
    if len(requests) > 100:
        raise HTTPException(status_code=400, detail="Batch size cannot exceed 100")

    results = []
    for req in requests:
        result = await embed_code(req)
        results.append(result)

    return results


@app.post("/similarity/code")
async def code_similarity(
    code1: str = Form(...),
    code2: str = Form(...),
    language: str = Form("python"),
):
    """
    Compute similarity between two code snippets.

    Args:
        code1: First code snippet
        code2: Second code snippet
        language: Programming language

    Returns:
        Cosine similarity score (0-1)
    """
    req1 = CodeEmbeddingRequest(code=code1, language=language)
    req2 = CodeEmbeddingRequest(code=code2, language=language)

    emb1 = await embed_code(req1)
    emb2 = await embed_code(req2)

    # Cosine similarity
    v1 = np.array(emb1.embedding)
    v2 = np.array(emb2.embedding)
    similarity = np.dot(v1, v2) / (np.linalg.norm(v1) * np.linalg.norm(v2))

    return {
        "similarity": float(similarity),
        "code1_language": language,
        "code2_language": language,
        "model": MODEL_NAME,
    }


@app.post("/embed/file")
async def embed_code_file(file: UploadFile = File(...), language: str = Form("python")):
    """
    Generate embedding from uploaded code file.

    Args:
        file: Source code file
        language: Programming language

    Returns:
        Embedding vector
    """
    try:
        content = await file.read()
        code = content.decode("utf-8")

        request = CodeEmbeddingRequest(code=code, language=language)
        return await embed_code(request)

    except UnicodeDecodeError:
        raise HTTPException(status_code=400, detail="File must be UTF-8 encoded text")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"File processing failed: {str(e)}")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8005)
