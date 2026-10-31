import pymupdf
from PIL import Image

from app.errors import ExtractionError
from app.ocr import OCR_DPI, clean_text, ocr_image

# A page with fewer characters than this is probably a scan (an image of text)
MIN_TEXT_CHARS = 30
MAX_PAGES = 300


def detect_kind(data: bytes) -> str | None:
    """Identify the file by its first bytes, never by its name."""
    if data.startswith(b"%PDF-"):
        return "pdf"
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png"
    if data.startswith(b"\xff\xd8\xff"):
        return "jpeg"
    return None


def _ocr_pdf_page(page: "pymupdf.Page") -> str:
    """Render one PDF page to a grayscale picture, then read it with OCR."""
    pix = page.get_pixmap(dpi=OCR_DPI, colorspace=pymupdf.csGRAY, alpha=False)
    image = Image.frombytes("L", (pix.width, pix.height), pix.samples)
    return ocr_image(image)


def extract_pdf(data: bytes, use_ocr: bool = True) -> list[dict]:
    """Return the text of each page of a PDF. Pages without text are read with OCR."""
    try:
        doc = pymupdf.open(stream=data, filetype="pdf")
    except Exception as exc:
        raise ExtractionError("The PDF could not be opened") from exc

    with doc:
        if doc.needs_pass:
            raise ExtractionError("The PDF is password protected")
        if doc.page_count > MAX_PAGES:
            raise ExtractionError(f"The PDF has more than {MAX_PAGES} pages")

        pages = []
        for number, page in enumerate(doc, start=1):
            text = clean_text(page.get_text("text"))
            needs_ocr = len(text) < MIN_TEXT_CHARS
            method = "text"

            if needs_ocr and use_ocr:
                text = _ocr_pdf_page(page)
                method = "ocr"

            pages.append({
                "page_number": number,
                "text": text,
                "char_count": len(text),
                "needs_ocr": needs_ocr,
                "method": method,
            })
    return pages