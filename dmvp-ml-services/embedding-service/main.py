"""
Phase 3 Step 3.2: SigLIP 2 Embedding Service (L4/L5)
Technology: Python 3.12 + HuggingFace Transformers
Model: google/siglip-2-large-patch16-512 (1.1B params, 2048d output)

Generates semantic embedding vectors for images and text.
Supports cross-modal retrieval (text-to-image, image-to-image).
"""

from fastapi import FastAPI, UploadFile, File, HTTPException, Form
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from typing import List, Optional
import torch
from transformers import AutoProcessor, AutoModel
from PIL import Image
import io
import numpy as np

app = FastAPI(
    title="SigLIP 2 Embedding Service",
    description="Phase 3 Step 3.2: Semantic embedding generation for TDD v5",
    version="3.2.0",
)

# Load model on startup
MODEL_NAME = "google/siglip-2-large-patch16-512"
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"

print(f"Loading SigLIP 2 model: {MODEL_NAME} on {DEVICE}")
processor = AutoProcessor.from_pretrained(MODEL_NAME)
model = AutoModel.from_pretrained(MODEL_NAME).to(DEVICE)
model.eval()

print("SigLIP 2 model loaded successfully")


class EmbeddingResponse(BaseModel):
    embedding: List[float]
    dimensions: int
    model_name: str
    vector_type: str


class TextEmbeddingRequest(BaseModel):
    text: str


@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {
        "status": "healthy",
        "model": MODEL_NAME,
        "device": DEVICE,
        "dimensions": 2048,
    }


@app.post("/embed/image", response_model=EmbeddingResponse)
async def embed_image(file: UploadFile = File(...)):
    """
    Generate 2048-dimensional embedding vector for an image.

    Returns:
        EmbeddingResponse with 2048-dim vector
    """
    try:
        # Read image
        image_bytes = await file.read()
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")

        # Process image
        inputs = processor(images=image, return_tensors="pt").to(DEVICE)

        # Generate embedding
        with torch.no_grad():
            outputs = model.get_image_features(**inputs)
            embedding = outputs.cpu().numpy()[0]

        # Normalize embedding
        embedding = embedding / np.linalg.norm(embedding)

        return EmbeddingResponse(
            embedding=embedding.tolist(),
            dimensions=2048,
            model_name=MODEL_NAME,
            vector_type="siglip",
        )

    except Exception as e:
        raise HTTPException(
            status_code=500, detail=f"Embedding generation failed: {str(e)}"
        )


@app.post("/embed/text", response_model=EmbeddingResponse)
async def embed_text(request: TextEmbeddingRequest):
    """
    Generate 2048-dimensional embedding vector for text.

    Returns:
        EmbeddingResponse with 2048-dim vector
    """
    try:
        # Process text
        inputs = processor(text=[request.text], return_tensors="pt", padding=True).to(
            DEVICE
        )

        # Generate embedding
        with torch.no_grad():
            outputs = model.get_text_features(**inputs)
            embedding = outputs.cpu().numpy()[0]

        # Normalize embedding
        embedding = embedding / np.linalg.norm(embedding)

        return EmbeddingResponse(
            embedding=embedding.tolist(),
            dimensions=2048,
            model_name=MODEL_NAME,
            vector_type="siglip",
        )

    except Exception as e:
        raise HTTPException(
            status_code=500, detail=f"Embedding generation failed: {str(e)}"
        )


@app.post("/embed/batch")
async def embed_batch(
    images: Optional[List[UploadFile]] = File(None), texts: Optional[str] = Form(None)
):
    """
    Batch embedding generation for multiple images and/or texts.
    """
    results = {
        "image_embeddings": [],
        "text_embeddings": [],
        "model_name": MODEL_NAME,
        "dimensions": 2048,
    }

    # Process images
    if images:
        for img_file in images:
            try:
                image_bytes = await img_file.read()
                image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
                inputs = processor(images=image, return_tensors="pt").to(DEVICE)

                with torch.no_grad():
                    outputs = model.get_image_features(**inputs)
                    embedding = outputs.cpu().numpy()[0]
                    embedding = embedding / np.linalg.norm(embedding)

                results["image_embeddings"].append(embedding.tolist())
            except Exception as e:
                results["image_embeddings"].append({"error": str(e)})

    # Process texts
    if texts:
        text_list = texts.split("\n") if "\n" in texts else [texts]
        inputs = processor(text=text_list, return_tensors="pt", padding=True).to(DEVICE)

        with torch.no_grad():
            outputs = model.get_text_features(**inputs)
            embeddings = outputs.cpu().numpy()

        for emb in embeddings:
            emb = emb / np.linalg.norm(emb)
            results["text_embeddings"].append(emb.tolist())

    return results


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8004)
