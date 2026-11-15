from scripts.common import BACKEND, call, login

IN_DOMAIN = [
    "What is the difference between a candidate key and a super key?",
    "What are the ACID properties?",
    "What is the default isolation level in MySQL?",
    "What is the difference between DELETE, TRUNCATE and DROP?",
    "What is the difference between WHERE and HAVING?",
    "What is a dirty read?",
    "What is the leftmost prefix rule for indexes?",
    "What does a LEFT JOIN return?",
    "What is first normal form?",
    "How do I create a stored procedure in MySQL?",
    "What does ON DELETE CASCADE do?",
    "What is a view in MySQL?",
    "How do I avoid storing the same data many times in a database?",
    "What happens if my money transfer fails halfway through?",
    "Which command empties a table but keeps its structure?",
]

RELATED_BUT_ABSENT = [
    "What is database sharding?",
    "Explain the CAP theorem.",
    "How does Redis caching work?",
]

OFF_TOPIC = [
    "What is the capital of France?",
    "How does a neural network learn?",
    "How do I bake a chocolate cake?",
    "Who won the last cricket world cup?",
    "How do I change a flat tyre?",
    "What is photosynthesis?",
    "How do I install Python on Windows?",
]


def run(title, questions, token):
    print(f"\n=== {title} ===")
    scores = []
    for question in questions:
        results = call("POST", f"{BACKEND}/api/search", {"question": question, "topK": 1}, token)
        if results:
            top = results[0]
            scores.append(top["similarity"])
            print(f"  {top['similarity']:.3f}  {question}  ->  {top['documentName']} p{top['pageNumber']}")
        else:
            print(f"  ----   {question}  ->  no results")
    return scores


def main():
    token = login("one")
    in_domain = run("ANSWERABLE from your notes", IN_DOMAIN, token)
    related = run("RELATED TOPIC but NOT in your notes", RELATED_BUT_ABSENT, token)
    off_topic = run("OFF TOPIC", OFF_TOPIC, token)

    print("\n=== SUMMARY ===")
    print(f"answerable : lowest {min(in_domain):.3f}  average {sum(in_domain) / len(in_domain):.3f}")
    print(f"related    : {min(related):.3f} to {max(related):.3f}")
    print(f"off topic  : highest {max(off_topic):.3f}")
    if max(off_topic) < min(in_domain):
        print("A threshold between the two numbers above separates answerable from off-topic questions.")
    else:
        print("The groups overlap: a single threshold cannot separate them cleanly.")


if __name__ == "__main__":
    main()