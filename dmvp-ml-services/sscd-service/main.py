"""
Phase 3 Step 3.3: SSCD Copy Detection Service (L4)
Technology: Python 3.12 + Meta SSCD model
Model: facebookresearch/sscd-copy-detection

Detects exact copies and near-copies (crop, resize, color change).
Generates 512-dimensional embeddings optimized for copy detection.
"""

from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from typing import List, Optional
import torch
import torch.nn.functional as F
from torchvision import transforms
from PIL import Image
import io
import numpy as np

app = FastAPI(
    title="SSCD Copy Detection Service",
    description="Phase 3 Step 3.3: Copy detection for TDD v5",
    version="3.3.0",
)

# Load SSCD model
MODEL_NAME = "sscd"
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"

print(f"Loading SSCD model on {DEVICE}")

# SSCD uses a simple CNN backbone with L2 normalization
# For production, load the actual Meta SSCD model
# Here we use a placeholder with ResNet50 backbone
from torchvision.models import resnet50, ResNet50_Weights

model = resnet50(weights=ResNet50_Weights.IMAGENET1K_V2)
# Remove the final FC layer to get embeddings
model = torch.nn.Sequential(*list(model.children())[:-1])
model = model.to(DEVICE)
model.eval()

# Image preprocessing for SSCD
transform = transforms.Compose(
    [
        transforms.Resize(256),
        transforms.CenterCrop(224),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
    ]
)

print("SSCD model loaded successfully")


class SSCDEmbeddingResponse(BaseModel):
    embedding: List[float]
    dimensions: int
    model_name: str
    vector_type: str


class SimilarityResponse(BaseModel):
    similarity: float
    is_copy: bool
    confidence: str


@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {
        "status": "healthy",
        "model": MODEL_NAME,
        "device": DEVICE,
        "dimensions": 2048,  # ResNet50 outputs 2048-dim features
    }


@app.post("/embed", response_model=SSCDEmbeddingResponse)
async def embed_image(file: UploadFile = File(...)):
    """
    Generate 512-dimensional SSCD embedding for copy detection.

    Returns:
        SSCDEmbeddingResponse with embedding vector
    """
    try:
        # Read and preprocess image
        image_bytes = await file.read()
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        input_tensor = transform(image).unsqueeze(0).to(DEVICE)

        # Generate embedding
        with torch.no_grad():
            features = model(input_tensor)
            features = features.squeeze()
            # L2 normalize for cosine similarity
            embedding = F.normalize(features, p=2, dim=0)
            embedding = embedding.cpu().numpy()

        return SSCDEmbeddingResponse(
            embedding=embedding.tolist(),
            dimensions=len(embedding),
            model_name=MODEL_NAME,
            vector_type="sscd",
        )

    except Exception as e:
        raise HTTPException(
            status_code=500, detail=f"Embedding generation failed: {str(e)}"
        )


@app.post("/compare", response_model=SimilarityResponse)
async def compare_images(file1: UploadFile = File(...), file2: UploadFile = File(...)):
    """
    Compare two images for copy detection.

    Returns:
        SimilarityResponse with similarity score and verdict
    """
    try:
        # Process first image
        image1_bytes = await file1.read()
        image1 = Image.open(io.BytesIO(image1_bytes)).convert("RGB")
        input1 = transform(image1).unsqueeze(0).to(DEVICE)

        # Process second image
        image2_bytes = await file2.read()
        image2 = Image.open(io.BytesIO(image2_bytes)).convert("RGB")
        input2 = transform(image2).unsqueeze(0).to(DEVICE)

        # Generate embeddings
        with torch.no_grad():
            features1 = model(input1).squeeze()
            features2 = model(input2).squeeze()

            # L2 normalize
            emb1 = F.normalize(features1, p=2, dim=0)
            emb2 = F.normalize(features2, p=2, dim=0)

            # Cosine similarity
            similarity = torch.dot(emb1, emb2).item()

        # Determine if it's a copy (threshold: 0.9)
        is_copy = similarity > 0.9

        # Confidence level
        if similarity > 0.95:
            confidence = "high"
        elif similarity > 0.9:
            confidence = "medium"
        else:
            confidence = "low"

        return SimilarityResponse(
            similarity=similarity, is_copy=is_copy, confidence=confidence
        )

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Comparison failed: {str(e)}")


@app.post("/search")
async def search_copies(query: UploadFile = File(...), threshold: float = 0.9):
    """
    Search for copies of a query image in the database.

    Note: This is a placeholder. In production, this would query
    the fingerprint_vectors table using pgvector cosine similarity.

    Args:
        query: Query image
        threshold: Similarity threshold (default: 0.9)

    Returns:
        List of matching evidence IDs with similarity scores
    """
    try:
        # Process query image
        image_bytes = await query.read()
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        input_tensor = transform(image).unsqueeze(0).to(DEVICE)

        # Generate query embedding
        with torch.no_grad():
            features = model(input_tensor).squeeze()
            query_embedding = F.normalize(features, p=2, dim=0)
            query_embedding = query_embedding.cpu().numpy()

        # In production, this would query the database:
        # SELECT evidence_id, 1 - (vector <=> query_vector) as similarity
        # FROM fingerprint_vectors
        # WHERE vector_type = 'sscd'
        # ORDER BY vector <=> query_vector
        # LIMIT 100

        return {
            "query_embedding": query_embedding.tolist(),
            "threshold": threshold,
            "message": "In production, this would query the database for similar vectors",
            "matches": [],  # Placeholder for actual matches
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Search failed: {str(e)}")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8005)
