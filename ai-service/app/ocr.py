import io
import os
import shutil

import pytesseract
from PIL import Image, ImageOps, UnidentifiedImageError

from app.errors import ExtractionError, OcrUnavailableError

OCR_LANG = os.getenv("OCR_LANG", "eng")
OCR_DPI = int(os.getenv("OCR_DPI", "300"))
OCR_TIMEOUT_SECONDS = int(os.getenv("OCR_TIMEOUT_SECONDS", "60"))

_WINDOWS_DEFAULT = r"C:\Program Files\Tesseract-OCR\tesseract.exe"


def _configure_tesseract() -> None:
    """Find the tesseract program: environment variable, then PATH, then the Windows default."""
    path = os.getenv("TESSERACT_CMD")
    if not path and not shutil.which("tesseract") and os.path.exists(_WINDOWS_DEFAULT):
        path = _WINDOWS_DEFAULT
    if path:
        pytesseract.pytesseract.tesseract_cmd = path


_configure_tesseract()


def clean_text(text: str) -> str:
    # PostgreSQL cannot store the NUL character, so remove it
    return text.replace("\x00", "").strip()


def ocr_image(image: Image.Image) -> str:
    """Run OCR on a Pillow image and return the text."""
    try:
        text = pytesseract.image_to_string(image, lang=OCR_LANG, timeout=OCR_TIMEOUT_SECONDS)
    except pytesseract.TesseractNotFoundError as exc:
        raise OcrUnavailableError("The OCR engine (Tesseract) is not installed on the server") from exc
    except RuntimeError as exc:
        # pytesseract raises RuntimeError when the timeout is exceeded
        raise ExtractionError("Reading the page took too long") from exc
    except pytesseract.TesseractError as exc:
        raise ExtractionError("The page could not be read") from exc
    return clean_text(text)


def ocr_image_bytes(data: bytes) -> str:
    """OCR for an uploaded PNG or JPEG file."""
    try:
        with Image.open(io.BytesIO(data)) as img:
            img.load()
            img = ImageOps.exif_transpose(img)   # phone photos can be stored rotated
            img = img.convert("L")               # grayscale
            return ocr_image(img)
    except (UnidentifiedImageError, OSError, Image.DecompressionBombError) as exc:
        raise ExtractionError("The image could not be opened") from exc