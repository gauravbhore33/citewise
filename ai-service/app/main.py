from typing import Annotated, Literal

from fastapi import FastAPI, File, HTTPException, UploadFile
from pydantic import BaseModel, Field

from app.chunking import CHUNK_WORDS, OVERLAP_WORDS, chunk_pages
from app.embedding import EMBEDDING_DIM, MODEL_NAME, embed_passages, embed_query
from app.errors import ExtractionError, OcrUnavailableError
from app.extraction import detect_kind, extract_pdf
from app.ocr import clean_text, ocr_image_bytes

app = FastAPI(title="CiteWise AI service")

MAX_FILE_BYTES = 25 * 1024 * 1024  # 25 MB, a little above the backend's 20 MB limit


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/extract")
def extract(file: UploadFile = File(...), ocr: bool = True):
    data = file.file.read()

    if not data:
        raise HTTPException(status_code=400, detail="The file is empty")
    if len(data) > MAX_FILE_BYTES:
        raise HTTPException(status_code=413, detail="The file is too large")

    kind = detect_kind(data)
    if kind is None:
        raise HTTPException(status_code=415, detail="Only PDF, PNG and JPEG files are supported")

    try:
        if kind == "pdf":
            pages = extract_pdf(data, use_ocr=ocr)
        else:
            text = clean_text(ocr_image_bytes(data))
            pages = [{
                "page_number": 1,
                "text": text,
                "char_count": len(text),
                "needs_ocr": True,
                "method": "ocr",
            }]
    except OcrUnavailableError as exc:
        raise HTTPException(status_code=503, detail=str(exc))
    except ExtractionError as exc:
        raise HTTPException(status_code=422, detail=str(exc))

    return {"page_count": len(pages), "pages": pages}


class PageIn(BaseModel):
    page_number: int = Field(ge=1)
    text: str


class ChunkRequest(BaseModel):
    pages: list[PageIn] = Field(max_length=300)
    chunk_words: int = Field(default=CHUNK_WORDS, ge=50, le=400)
    overlap_words: int = Field(default=OVERLAP_WORDS, ge=0, le=200)


@app.post("/chunk")
def chunk(request: ChunkRequest):
    if request.overlap_words * 2 > request.chunk_words:
        raise HTTPException(status_code=422, detail="overlap_words must be at most half of chunk_words")

    chunks = chunk_pages([p.model_dump() for p in request.pages],
                         request.chunk_words, request.overlap_words)
    return {"chunk_count": len(chunks), "chunks": chunks}


class EmbedRequest(BaseModel):
    texts: list[Annotated[str, Field(min_length=1, max_length=8000)]] = Field(min_length=1, max_length=64)
    kind: Literal["passage", "query"] = "passage"


@app.post("/embed")
def embed(request: EmbedRequest):
    if request.kind == "query":
        vectors = [embed_query(text) for text in request.texts]
    else:
        vectors = embed_passages(request.texts)
    return {"model": MODEL_NAME, "dimension": EMBEDDING_DIM, "embeddings": vectors}