import json
import sys
import urllib.error
import urllib.request

BACKEND = "http://localhost:8080"
AI_SERVICE = "http://localhost:8000"
USERS = {"one": "one@test.com", "two": "two@test.com"}

# print any character without crashing the Windows console
sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def _request(method, url, body, token):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    return request


def call(method, url, body=None, token=None, timeout=180):
    try:
        with urllib.request.urlopen(_request(method, url, body, token), timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raise SystemExit(f"HTTP {error.code} from {url}\n{error.read().decode('utf-8', 'replace')[:300]}")


def call_safe(method, url, body=None, token=None, timeout=180):
    """Like call(), but returns (status_code, data) instead of stopping on an HTTP error."""
    try:
        with urllib.request.urlopen(_request(method, url, body, token), timeout=timeout) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8", "replace")[:300]
    except urllib.error.URLError as error:
        raise SystemExit(f"Cannot reach {url}: {error.reason}")


def login(user="one"):
    result = call("POST", f"{BACKEND}/api/auth/login", {"email": USERS[user], "password": "password123"})
    return result["token"]