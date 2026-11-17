import json
import sys
from collections import Counter
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
RESULTS_DIR = Path(__file__).resolve().parents[2] / "eval" / "results"
COLUMN = 30

ROWS = [
    ("hit@1", "retrieval hit@1", "answerable"),
    ("hit@3", "retrieval hit@3", "answerable"),
    ("hit@5", "retrieval hit@5", "answerable"),
    ("mrr", "mean reciprocal rank", None),
    ("answered", "answerable: answered", "answerable"),
    ("false_refusals", "answerable: wrongly refused (bad)", "answerable"),
    ("correct_refusals", "unanswerable: correctly refused", "unanswerable"),
    ("false_answers", "unanswerable: wrongly answered (bad)", "unanswerable"),
    ("supported", "answers whose citation holds the answer", "answered"),
    ("errors", "questions that errored", None),
]


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))["rows"]


def metrics(rows):
    ans = [r for r in rows if r["answerable"]]
    unans = [r for r in rows if not r["answerable"]]
    m = {"answerable": len(ans), "unanswerable": len(unans)}
    for k in (1, 3, 5):
        m[f"hit@{k}"] = sum(1 for r in ans if r["hit_rank"] is not None and r["hit_rank"] <= k)
    m["mrr"] = sum(1 / r["hit_rank"] for r in ans if r["hit_rank"]) / len(ans) if ans else 0.0
    if rows and "status" in rows[0]:
        m["answered"] = sum(1 for r in ans if r["status"] == "ANSWERED")
        m["false_refusals"] = sum(1 for r in ans if r["status"] == "NOT_FOUND")
        m["correct_refusals"] = sum(1 for r in unans if r["status"] == "NOT_FOUND")
        m["false_answers"] = sum(1 for r in unans if r["status"] == "ANSWERED")
        m["supported"] = sum(1 for r in ans if r["status"] == "ANSWERED" and r.get("cited_supports_answer"))
        m["errors"] = sum(1 for r in rows if r["status"] not in ("ANSWERED", "NOT_FOUND"))
        m["layers"] = dict(Counter(r["reason"] for r in unans if r["status"] == "NOT_FOUND"))
    return m


def fmt(m, key, denom):
    if key not in m:
        return "n/a"
    value = m[key]
    if key == "mrr":
        return f"{value:.3f}"
    if denom is None:
        return str(value)
    total = m[denom]
    return f"{value}/{total} ({100 * value / total:.0f}%)" if total else str(value)


def problems(rows):
    """Questions worth a look: wrong refusals, wrong answers, weak citations and errors."""
    lines = []
    for r in rows:
        if "status" not in r:
            continue
        top = r["top_similarity"]
        if r["answerable"] and r["status"] == "NOT_FOUND":
            lines.append(f"  wrongly refused   {r['id']} [{r['reason']}] similarity {top:.3f}: {r['question']}")
        elif not r["answerable"] and r["status"] == "ANSWERED":
            lines.append(f"  wrongly answered  {r['id']} similarity {top:.3f}: {r['question']}")
        elif r["answerable"] and r["status"] == "ANSWERED" and not r.get("cited_supports_answer"):
            lines.append(f"  weak citation     {r['id']}: {r['question']}  (top pages: {', '.join(r['top_pages'])})")
        elif r["status"] not in ("ANSWERED", "NOT_FOUND"):
            lines.append(f"  error             {r['id']} ({r.get('reason')}): {r['question']}")
    return lines


def main():
    if sys.argv[1:]:
        paths = [Path(a) if Path(a).exists() else RESULTS_DIR / a for a in sys.argv[1:]]
    else:   # no arguments: the four most recent result files
        paths = sorted(RESULTS_DIR.glob("*.json"), key=lambda p: p.stat().st_mtime)[-4:]
    if not paths:
        raise SystemExit(f"No result files in {RESULTS_DIR}")

    loaded = [(p, load(p)) for p in paths]
    results = [(p.stem[:COLUMN - 2], metrics(rows)) for p, rows in loaded]

    print(f"{'':42}" + "".join(f"{name:>{COLUMN}}" for name, _ in results))
    for key, label, denom in ROWS:
        print(f"{label:42}" + "".join(f"{fmt(m, key, denom):>{COLUMN}}" for _, m in results))

    for (path, rows), (name, m) in zip(loaded, results):
        if "layers" not in m:
            continue
        print(f"\n{name}: refusals by layer {m['layers']}")
        found = problems(rows)
        print("\n".join(found) if found else "  no wrong answers, wrong refusals or weak citations")


if __name__ == "__main__":
    main()