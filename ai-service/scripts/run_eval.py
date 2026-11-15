import argparse
import json
import time
from collections import Counter
from datetime import datetime
from pathlib import Path

from scripts.common import BACKEND, call_safe, login

ROOT = Path(__file__).resolve().parents[2]
QUESTIONS_FILE = ROOT / "eval" / "questions.json"
RESULTS_DIR = ROOT / "eval" / "results"
THRESHOLDS = [0.40, 0.45, 0.50, 0.55, 0.60, 0.65, 0.70]


def normalise(text):
    return " ".join(text.lower().split())


def contains_any(text, phrases):
    haystack = normalise(text)
    return any(normalise(p) in haystack for p in phrases)


def pct(count, total):
    return f"{100 * count / total:5.1f}%" if total else "  n/a"


def ask_chat(token, question):
    for attempt in range(3):
        code, data = call_safe("POST", f"{BACKEND}/api/chat", {"question": question}, token)
        if code == 200:
            return data
        if code in (429, 503) and attempt < 2:
            time.sleep(10 * (attempt + 1))     # the model service may be rate limited: wait and retry
            continue
        return {"status": "ERROR", "reason": f"HTTP {code}", "hasCitations": False, "sources": []}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--no-chat", action="store_true", help="measure retrieval only (no language model calls)")
    parser.add_argument("--pause", type=float, default=3.0, help="seconds to wait between model calls")
    parser.add_argument("--top", type=int, default=5)
    args = parser.parse_args()

    questions = json.loads(QUESTIONS_FILE.read_text(encoding="utf-8"))
    token = login("one")
    rows = []

    for q in questions:
        code, results = call_safe("POST", f"{BACKEND}/api/search",
                                  {"question": q["question"], "topK": args.top}, token)
        if code != 200:
            raise SystemExit(f"Search failed with HTTP {code}: {results}")

        top = results[0]["similarity"] if results else 0.0
        hit_rank = None
        if q["answerable"]:
            for rank, r in enumerate(results, start=1):
                if contains_any(r["text"], q["expected_any"]):
                    hit_rank = rank
                    break

        row = {
            "id": q["id"], "category": q["category"], "question": q["question"],
            "answerable": q["answerable"], "top_similarity": top, "hit_rank": hit_rank,
            "top_pages": [f"{r['documentName']} p{r['pageNumber']}" for r in results[:3]],
        }

        if not args.no_chat:
            chat = ask_chat(token, q["question"])
            supports = None
            if q["answerable"] and chat.get("status") == "ANSWERED":
                supports = any(s["cited"] and contains_any(s["text"], q["expected_any"])
                               for s in chat["sources"])
            row.update({"status": chat.get("status"), "reason": chat.get("reason"),
                        "has_citations": chat.get("hasCitations"), "cited_supports_answer": supports})
            time.sleep(args.pause)

        rows.append(row)
        print(".", end="", flush=True)
    print()

    answerable = [r for r in rows if r["answerable"]]
    unanswerable = [r for r in rows if not r["answerable"]]

    print(f"\n{len(answerable)} answerable and {len(unanswerable)} unanswerable questions")

    print("\n=== RETRIEVAL: does the passage with the answer appear in the top results? ===")
    for k in (1, 3, 5):
        hits = sum(1 for r in answerable if r["hit_rank"] is not None and r["hit_rank"] <= k)
        print(f"  hit@{k}: {hits:2d} of {len(answerable)}   {pct(hits, len(answerable))}")
    mrr = sum(1 / r["hit_rank"] for r in answerable if r["hit_rank"]) / len(answerable)
    print(f"  mean reciprocal rank: {mrr:.3f}")

    print("\n=== LAYER-1 THRESHOLD SWEEP (similarity only, no language model) ===")
    print("  threshold   answerable questions refused (bad)   unanswerable stopped early (good)")
    for t in THRESHOLDS:
        bad = sum(1 for r in answerable if r["top_similarity"] < t)
        good = sum(1 for r in unanswerable if r["top_similarity"] < t)
        print(f"    {t:.2f}              {bad:2d} of {len(answerable)}                          {good:2d} of {len(unanswerable)}")

    misses = [r for r in answerable if r["hit_rank"] is None or r["hit_rank"] > 1]
    if misses:
        print("\n=== ANSWERABLE QUESTIONS WHOSE ANSWER PASSAGE WAS NOT RANKED FIRST ===")
        for r in misses:
            where = "not in the top results" if r["hit_rank"] is None else f"found at rank {r['hit_rank']}"
            print(f"  {r['id']}  {where}   top results: {', '.join(r['top_pages'])}")
            print(f"        {r['question']}")

    if not args.no_chat:
        answered = [r for r in answerable if r["status"] == "ANSWERED"]
        false_refusals = [r for r in answerable if r["status"] == "NOT_FOUND"]
        correct_refusals = [r for r in unanswerable if r["status"] == "NOT_FOUND"]
        false_answers = [r for r in unanswerable if r["status"] == "ANSWERED"]
        errors = [r for r in rows if r["status"] not in ("ANSWERED", "NOT_FOUND")]
        supported = [r for r in answered if r["cited_supports_answer"]]

        print("\n=== END TO END (retrieve, refuse or answer, cite) ===")
        print(f"  answerable questions answered:       {len(answered):2d} of {len(answerable)}   {pct(len(answered), len(answerable))}")
        print(f"  answerable questions refused (bad):  {len(false_refusals):2d} of {len(answerable)}   {pct(len(false_refusals), len(answerable))}")
        print(f"  unanswerable correctly refused:      {len(correct_refusals):2d} of {len(unanswerable)}   {pct(len(correct_refusals), len(unanswerable))}")
        print(f"  unanswerable wrongly answered (bad): {len(false_answers):2d} of {len(unanswerable)}   {pct(len(false_answers), len(unanswerable))}")
        print(f"  answers whose citation holds the answer text: {len(supported)} of {len(answered)}   {pct(len(supported), len(answered))}")
        print(f"  refusals by layer: {dict(Counter(r['reason'] for r in correct_refusals))}")
        if errors:
            print(f"  errors (not counted above): {[r['id'] for r in errors]}")
        for r in false_refusals + false_answers:
            print(f"  CHECK {r['id']} [{r['status']}/{r['reason']}] similarity {r['top_similarity']:.3f}: {r['question']}")

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    out = RESULTS_DIR / f"run_{datetime.now():%Y%m%d_%H%M}.json"
    out.write_text(json.dumps({"created": datetime.now().isoformat(timespec="seconds"), "rows": rows},
                              indent=2), encoding="utf-8")
    print(f"\nSaved: {out}")


if __name__ == "__main__":
    main()