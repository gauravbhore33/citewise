from fastapi import FastAPI, File, HTTPException, UploadFile

from app.extraction import ExtractionError, extract_pdf

app = FastAPI(title="CiteWise AI service")

MAX_FILE_BYTES = 25 * 1024 * 1024  # 25 MB, a little above the backend's 20 MB limit


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/extract")
def extract(file: UploadFile = File(...)):
    data = file.file.read()

    if not data:
        raise HTTPException(status_code=400, detail="The file is empty")
    if len(data) > MAX_FILE_BYTES:
        raise HTTPException(status_code=413, detail="The file is too large")
    if not data.startswith(b"%PDF-"):
        raise HTTPException(status_code=415, detail="Only PDF files are supported so far")

    try:
        pages = extract_pdf(data)
    except ExtractionError as exc:
        raise HTTPException(status_code=422, detail=str(exc))

    return {"page_count": len(pages), "pages": pages}