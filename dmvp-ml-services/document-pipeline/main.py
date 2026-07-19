"""
TDD v5 Phase 2 Step 2.3: PDF/Document Pipeline (L2)
Technology: Python 3.12 + Docling (IBM, 2024)

Extracts structural fingerprint from PDFs:
- Page count, heading tree, table positions
- Structural hash that remains same even if text changes
- Detects formatting changes

Why Docling: Better structure extraction than Apache PDFBox
"""

from fastapi import FastAPI, UploadFile, File, HTTPException
from pydantic import BaseModel
from typing import Optional, List, Dict, Any
import hashlib
import json
import tempfile
import os
from pathlib import Path

app = FastAPI(title="DMVP Document Pipeline", version="5.0.0")

# Try to import docling, fallback to basic PDF parsing
try:
    from docling.document_converter import DocumentConverter

    DOCLING_AVAILABLE = True
except ImportError:
    DOCLING_AVAILABLE = False
    print("Warning: Docling not available, using fallback PDF parsing")


class StructuralFingerprint(BaseModel):
    """L2 Structural fingerprint for documents"""

    page_count: int
    heading_tree: List[Dict[str, Any]]
    table_count: int
    table_positions: List[Dict[str, int]]
    image_count: int
    structural_hash: str
    text_length: int
    avg_paragraph_length: float


class FingerprintResponse(BaseModel):
    success: bool
    fingerprint: Optional[StructuralFingerprint] = None
    error: Optional[str] = None
    modality: str = "document"
    layer: str = "L2"


def extract_structure_with_docling(file_path: str) -> Dict[str, Any]:
    """Extract document structure using Docling"""
    converter = DocumentConverter()
    result = converter.convert(file_path)

    # Extract heading tree
    headings = []
    for item in result.document.iterate_items():
        if hasattr(item, "label") and "heading" in str(item.label).lower():
            headings.append(
                {
                    "level": getattr(item, "level", 0),
                    "text": getattr(item, "text", "")[:100],  # Truncate for hash
                    "page": getattr(item, "page_no", 0),
                }
            )

    # Count tables and images
    tables = [
        item
        for item in result.document.iterate_items()
        if hasattr(item, "label") and "table" in str(item.label).lower()
    ]
    images = [
        item
        for item in result.document.iterate_items()
        if hasattr(item, "label") and "image" in str(item.label).lower()
    ]

    return {
        "page_count": len(result.document.pages),
        "heading_tree": headings,
        "table_count": len(tables),
        "table_positions": [
            {"page": getattr(t, "page_no", 0), "row": getattr(t, "row_idx", 0)}
            for t in tables
        ],
        "image_count": len(images),
        "text_length": len(result.document.export_to_markdown()),
        "paragraphs": [
            len(getattr(p, "text", ""))
            for p in result.document.iterate_items()
            if hasattr(p, "text")
        ],
    }


def extract_structure_fallback(file_path: str) -> Dict[str, Any]:
    """Fallback PDF structure extraction without Docling"""
    import PyPDF2

    with open(file_path, "rb") as f:
        reader = PyPDF2.PdfReader(f)
        page_count = len(reader.pages)

        # Basic text extraction
        text_length = 0
        paragraphs = []
        for page in reader.pages:
            text = page.extract_text()
            if text:
                text_length += len(text)
                paragraphs.append(len(text))

        return {
            "page_count": page_count,
            "heading_tree": [],  # Cannot extract without Docling
            "table_count": 0,
            "table_positions": [],
            "image_count": 0,
            "text_length": text_length,
            "paragraphs": paragraphs,
        }


def compute_structural_hash(structure: Dict[str, Any]) -> str:
    """
    Compute hash of document structure.
    Remains same even if text changes, detects formatting changes.
    """
    # Hash only structural elements, not text content
    hash_input = {
        "page_count": structure["page_count"],
        "heading_levels": [h.get("level", 0) for h in structure["heading_tree"]],
        "heading_count": len(structure["heading_tree"]),
        "table_count": structure["table_count"],
        "image_count": structure["image_count"],
        "table_positions": structure["table_positions"],
    }

    hash_json = json.dumps(hash_input, sort_keys=True)
    return hashlib.sha256(hash_json.encode()).hexdigest()


@app.post("/fingerprint/document", response_model=FingerprintResponse)
async def compute_document_fingerprint(file: UploadFile = File(...)):
    """
    Compute L2 structural fingerprint for PDF/document
    """
    if not file.filename.lower().endswith(".pdf"):
        raise HTTPException(status_code=400, detail="Only PDF files supported")

    # Save uploaded file temporarily
    with tempfile.NamedTemporaryFile(delete=False, suffix=".pdf") as tmp:
        content = await file.read()
        tmp.write(content)
        tmp_path = tmp.name

    try:
        # Extract structure
        if DOCLING_AVAILABLE:
            structure = extract_structure_with_docling(tmp_path)
        else:
            structure = extract_structure_fallback(tmp_path)

        # Compute structural hash
        structural_hash = compute_structural_hash(structure)

        # Calculate average paragraph length
        paragraphs = structure.get("paragraphs", [])
        avg_paragraph_length = sum(paragraphs) / len(paragraphs) if paragraphs else 0.0

        fingerprint = StructuralFingerprint(
            page_count=structure["page_count"],
            heading_tree=structure["heading_tree"][:20],  # Limit for response size
            table_count=structure["table_count"],
            table_positions=structure["table_positions"][:50],  # Limit
            image_count=structure["image_count"],
            structural_hash=structural_hash,
            text_length=structure["text_length"],
            avg_paragraph_length=avg_paragraph_length,
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
        "service": "document-pipeline",
        "docling_available": DOCLING_AVAILABLE,
        "version": "5.0.0",
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8002)
