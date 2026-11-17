import json
import urllib.request

text = " ".join(f"{i}. What is thing number {i} in databases?" for i in range(1, 60))
request = urllib.request.Request(
    "http://localhost:8000/chunk",
    data=json.dumps({"pages": [{"page_number": 1, "text": text}]}).encode("utf-8"),
    headers={"Content-Type": "application/json"},
)
count = json.loads(urllib.request.urlopen(request).read().decode("utf-8"))["chunk_count"]
print("chunks returned for a list of 59 questions:", count)
print("filter is ACTIVE" if count == 0 else "filter is NOT active in the running service")