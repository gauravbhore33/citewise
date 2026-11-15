import argparse

from scripts.common import AI_SERVICE, BACKEND, call, login


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("question")
    parser.add_argument("--top", type=int, default=5)
    parser.add_argument("--min-sim", type=float, default=0.0, help="refuse if the best match scores below this")
    args = parser.parse_args()

    token = login("one")
    results = call("POST", f"{BACKEND}/api/search", {"question": args.question, "topK": args.top}, token)

    print(f"\nQuestion: {args.question}")
    best = results[0]["similarity"] if results else 0.0
    if not results or best < args.min_sim:
        print(f"-> NOT FOUND (layer 1: best similarity {best:.3f} is below {args.min_sim}). The model was not called.")
        return

    contexts = [
        {"n": i + 1, "document": r["documentName"], "page": r["pageNumber"], "text": r["text"]}
        for i, r in enumerate(results)
    ]
    reply = call("POST", f"{AI_SERVICE}/answer", {"question": args.question, "contexts": contexts})

    if reply["not_found"]:
        print(f"-> NOT FOUND (layer 2: the model said the passages do not answer it; best similarity {best:.3f})")
        return

    print("\nAnswer:\n" + reply["answer"])
    print("\nSources the answer cites:")
    for n in reply["cited"]:
        r = results[n - 1]
        print(f"  [{n}] {r['documentName']}, page {r['pageNumber']}  (similarity {r['similarity']:.3f})")
    if not reply["cited"]:
        print("  (the model cited nothing: treat this answer with caution)")


if __name__ == "__main__":
    main()