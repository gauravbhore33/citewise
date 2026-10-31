from fastapi import FastAPI, File, HTTPException, UploadFile

from app.errors import ExtractionError, OcrUnavailableError
from app.extraction import detect_kind, extract_pdf
from app.ocr import ocr_image_bytes, clean_text

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