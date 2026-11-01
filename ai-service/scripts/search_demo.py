import argparse
import json
import sys
import urllib.request

BACKEND = "http://localhost:8080"
USERS = {"one": "one@test.com", "two": "two@test.com"}

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
    parser = argparse.ArgumentParser()
    parser.add_argument("question")
    parser.add_argument("--top", type=int, default=5)
    parser.add_argument("--as", dest="user", default="one", choices=sorted(USERS))
    args = parser.parse_args()

    login = call("POST", f"{BACKEND}/api/auth/login",
                 {"email": USERS[args.user], "password": "password123"})
    results = call("POST", f"{BACKEND}/api/search",
                   {"question": args.question, "topK": args.top}, login["token"])

    print(f"\nQuestion: {args.question}   (as user {args.user})")
    if not results:
        print("  (no results)")
        return
    for r in results:
        snippet = r["text"][:150].replace("\n", " ")
        print(f"  {r['similarity']:.3f}  {r['documentName']}  page {r['pageNumber']}")
        print(f"         {snippet}...")


if __name__ == "__main__":
    main()