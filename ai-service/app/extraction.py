import pymupdf

# A page with fewer characters than this is probably a scan (an image of text)
MIN_TEXT_CHARS = 30


class ExtractionError(Exception):
    """Raised when a file cannot be read; the message is safe to show to the caller."""


def extract_pdf(data: bytes) -> list[dict]:
    """Return the text of each page of a PDF as a list of dictionaries."""
    try:
        doc = pymupdf.open(stream=data, filetype="pdf")
    except Exception as exc:
        raise ExtractionError("The PDF could not be opened") from exc

    with doc:
        if doc.needs_pass:
            raise ExtractionError("The PDF is password protected")

        pages = []
        for number, page in enumerate(doc, start=1):
            # PostgreSQL cannot store the NUL character, so remove it now
            text = page.get_text("text").replace("\x00", "").strip()
            pages.append({
                "page_number": number,
                "text": text,
                "char_count": len(text),
                "needs_ocr": len(text) < MIN_TEXT_CHARS,
                "method": "text",
            })
    return pages