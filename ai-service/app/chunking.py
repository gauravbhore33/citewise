import os
import re

CHUNK_WORDS = int(os.getenv("CHUNK_WORDS", "200"))
OVERLAP_WORDS = int(os.getenv("OVERLAP_WORDS", "40"))
MIN_CHUNK_WORDS = int(os.getenv("MIN_CHUNK_WORDS", "8"))   # smaller pieces carry too little meaning
MIN_NEW_WORDS = 10                                         # avoid a last chunk that only repeats the overlap
SENTENCE_END = (".", "!", "?")

# decoration such as ".........." or "-----" or ". . . . ." (five or more in a row)
_DECORATION = re.compile(r"(?:[.\-_=*~]\s?){5,}")
# a word broken by a hyphen at the end of a line: "informa-\ntion"
_HYPHEN_BREAK = re.compile(r"(?<=[a-z])-\n(?=[a-z])")


def clean_text(text: str) -> str:
    """Remove layout noise and return the page as one line of text."""
    text = text.replace("\x00", "")
    text = _HYPHEN_BREAK.sub("", text)
    text = _DECORATION.sub(" ", text)

    lines = []
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        if not any(ch.isalpha() for ch in line):
            continue                 # page numbers and lone symbols
        lines.append(line)
    return " ".join(lines)


def chunk_page(text: str, size: int = CHUNK_WORDS, overlap: int = OVERLAP_WORDS,
               min_words: int = MIN_CHUNK_WORDS) -> list[str]:
    """Split one page of text into overlapping chunks of about `size` words."""
    words = text.split()
    n = len(words)
    chunks = []
    start = 0

    while start < n:
        end = min(start + size, n)

        if end < n:
            # finish at the end of a sentence if there is one in the last 30% of the window
            for i in range(end - 1, start + int(size * 0.7) - 1, -1):
                if words[i].endswith(SENTENCE_END):
                    end = i + 1
                    break
            # do not leave a tiny tail that would only repeat the overlap
            if n - end < MIN_NEW_WORDS:
                end = n

        piece = words[start:end]
        if len(piece) >= min_words:
            chunks.append(" ".join(piece))

        if end >= n:
            break
        start = max(end - overlap, start + 1)    # step back by the overlap, always move forward

    return chunks


def chunk_pages(pages: list[dict], size: int = CHUNK_WORDS, overlap: int = OVERLAP_WORDS) -> list[dict]:
    """Chunk every page of a document. Chunks never cross a page boundary."""
    result = []
    index = 0
    for page in sorted(pages, key=lambda p: p["page_number"]):
        text = clean_text(page["text"])
        for piece in chunk_page(text, size, overlap):
            result.append({
                "chunk_index": index,
                "page_number": page["page_number"],
                "text": piece,
                "word_count": len(piece.split()),
            })
            index += 1
    return result