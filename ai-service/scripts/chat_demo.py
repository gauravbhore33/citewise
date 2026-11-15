import argparse

from scripts.common import BACKEND, call, login


def show(message):
    print(f"\n#{message['id']}  [{message['status']}"
          + (f" / {message['reason']}" if message.get("reason") else "") + "]")
    print("Q:", message["question"])
    top = message.get("topSimilarity")
    print("top similarity:", "n/a" if top is None else f"{top:.3f}")
    print("A:", message["answer"])
    for source in message["sources"]:
        mark = "cited" if source["cited"] else "     "
        print(f"   [{source['n']}] {mark}  {source['documentName']} p{source['pageNumber']}  ({source['similarity']:.3f})")
    if message["status"] == "ANSWERED" and not message["hasCitations"]:
        print("   (no valid citations: treat this answer with caution)")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("question", nargs="?")
    parser.add_argument("--as", dest="user", default="one", choices=["one", "two"])
    parser.add_argument("--history", action="store_true")
    args = parser.parse_args()

    token = login(args.user)
    if args.history:
        for message in call("GET", f"{BACKEND}/api/chat/history?limit=10", token=token):
            show(message)
    elif args.question:
        show(call("POST", f"{BACKEND}/api/chat", {"question": args.question}, token))
    else:
        parser.error("give a question, or use --history")


if __name__ == "__main__":
    main()