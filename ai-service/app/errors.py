class ExtractionError(Exception):
    """The file could not be read. The message is safe to show to the caller."""


class OcrUnavailableError(Exception):
    """The OCR engine is not available on this server."""