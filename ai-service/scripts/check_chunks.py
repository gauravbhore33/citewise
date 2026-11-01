import json
import sys
import urllib.request

BACKEND = "http://localhost:8080"
AI_SERVICE = "http://localhost:8000"
OVERLAP = 40   # must match OVERLAP_WORDS in app/chunking.py

# print any character without crashing the Windows console
sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def call(method, url, body=None, token=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.loads(response.read().decode("utf-8"))


def main():
    ids = [int(arg) for arg in sys.argv[1:]]
    if not ids:
        print("usage: python -m scripts.check_chunks 42 43")
        return

    login = call("POST", f"{BACKEND}/api/auth/login",
                 {"email": "one@test.com", "password": "password123"})
    token = login["token"]

    for doc_id in ids:
        pages = call("GET", f"{BACKEND}/api/documents/{doc_id}/pages", token=token)
        garbled = sum(1 for p in pages if "\u00e2" in p["text"])   # the letter a with a hat
        payload = {"pages": [{"page_number": p["pageNumber"], "text": p["text"]} for p in pages]}
        result = call("POST", f"{AI_SERVICE}/chunk", payload)
        chunks = result["chunks"]

        words = [c["word_count"] for c in chunks]
        print(f"\n=== document {doc_id} ===")
        print(f"pages: {len(pages)}   chunks: {len(chunks)}")
        if not words:
            print("no chunks were produced")
            continue
        print(f"words per chunk: average {sum(words) / len(words):.0f}, min {min(words)}, max {max(words)}")
        print(f"pages containing the garbled letter: {garbled}  (0 means the stored text is clean)")

        # overlap check: consecutive chunks on the same page must share the last/first words
        pairs = [(a, b) for a, b in zip(chunks, chunks[1:]) if a["page_number"] == b["page_number"]]
        ok = sum(1 for a, b in pairs if a["text"].split()[-OVERLAP:] == b["text"].split()[:OVERLAP])
        print(f"overlap verified for {ok} of {len(pairs)} consecutive same-page pairs")

        print("\nfirst chunk:\n" + chunks[0]["text"][:600])
        if pairs:
            a, b = pairs[0]
            print(f"\nend of a chunk on page {a['page_number']}:\n..." + a["text"][-250:])
            print("start of the next chunk:\n" + b["text"][:250] + "...")


if __name__ == "__main__":
    main()