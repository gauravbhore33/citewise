import time
from functools import lru_cache

import httpx

from app import config

RETRY_DELAYS_SECONDS = (1.0, 3.0)                  # waits before the 2nd and 3rd attempt
RETRYABLE_STATUS = {429, 500, 502, 503, 504}
MAX_RETRY_AFTER_SECONDS = 10.0


class LlmError(Exception):
    """The language model could not be used. The message is safe to show to the caller."""

    def __init__(self, message: str, status_code: int = 502):
        super().__init__(message)
        self.status_code = status_code


class OpenAICompatibleProvider:
    """Talks to any server that offers the OpenAI chat-completions API (Groq, OpenAI, Ollama...)."""

    def __init__(self, base_url: str, model: str, api_key: str, timeout: float, max_tokens: int):
        self.base_url = base_url
        self.model = model
        self.api_key = api_key
        self.timeout = timeout
        self.max_tokens = max_tokens

    def complete(self, system: str, user: str) -> str:
        if not (self.base_url and self.model and self.api_key):
            raise LlmError("The language model is not configured on the server", 503)

        payload = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
            "temperature": 0,
            "max_tokens": self.max_tokens,
        }
        attempts = len(RETRY_DELAYS_SECONDS) + 1
        last_error: LlmError | None = None
        delay = 0.0

        for attempt in range(attempts):
            delay = RETRY_DELAYS_SECONDS[attempt] if attempt < attempts - 1 else 0.0
            try:
                response = httpx.post(
                    f"{self.base_url}/chat/completions",
                    headers={"Authorization": f"Bearer {self.api_key}"},
                    json=payload,
                    timeout=self.timeout,
                )
            except httpx.TimeoutException as exc:
                # we already waited the full timeout, so do not wait again
                raise LlmError("The language model took too long to answer", 504) from exc
            except httpx.HTTPError:
                last_error = LlmError("The language model could not be reached", 502)
            else:
                code = response.status_code
                if code in (401, 403):
                    raise LlmError("The language model rejected the server's API key", 502)
                if code in RETRYABLE_STATUS:
                    if code == 429:
                        last_error = LlmError("The language model is busy (rate limit). Please try again shortly.", 503)
                        retry_after = response.headers.get("retry-after", "")
                        if retry_after.replace(".", "", 1).isdigit():
                            delay = min(float(retry_after), MAX_RETRY_AFTER_SECONDS)
                    else:
                        last_error = LlmError(f"The language model returned an error ({code})", 502)
                elif code >= 400:
                    raise LlmError(f"The language model returned an error ({code})", 502)
                else:
                    return self._read_answer(response)

            if attempt < attempts - 1:
                time.sleep(delay)

        raise last_error or LlmError("The language model could not be used", 502)

    @staticmethod
    def _read_answer(response: httpx.Response) -> str:
        try:
            return response.json()["choices"][0]["message"]["content"] or ""
        except (KeyError, IndexError, ValueError, TypeError) as exc:
            raise LlmError("The language model gave an unusable answer", 502) from exc


@lru_cache(maxsize=1)
def get_provider() -> OpenAICompatibleProvider:
    return OpenAICompatibleProvider(
        base_url=config.LLM_BASE_URL,
        model=config.LLM_MODEL,
        api_key=config.LLM_API_KEY,
        timeout=config.LLM_TIMEOUT_SECONDS,
        max_tokens=config.LLM_MAX_TOKENS,
    )