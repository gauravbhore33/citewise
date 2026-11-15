import httpx
import pytest

from app import llm as llm_module
from app.llm import LlmError, OpenAICompatibleProvider

GOOD = {"choices": [{"message": {"content": "ready"}}]}


def make_provider():
    return OpenAICompatibleProvider("https://example.test/v1", "model", "key", 5.0, 100)


def fake_response(status, body=None, headers=None):
    request = httpx.Request("POST", "https://example.test/v1/chat/completions")
    return httpx.Response(status, json=body or {}, headers=headers or {}, request=request)


def install(monkeypatch, replies):
    """Make httpx.post return the given replies in order, and count the calls."""
    calls = []

    def fake_post(*args, **kwargs):
        calls.append(1)
        return replies.pop(0)

    monkeypatch.setattr(llm_module.httpx, "post", fake_post)
    monkeypatch.setattr(llm_module.time, "sleep", lambda seconds: None)
    return calls


def test_a_server_error_is_retried_and_then_succeeds(monkeypatch):
    calls = install(monkeypatch, [fake_response(500), fake_response(200, GOOD)])
    assert make_provider().complete("s", "u") == "ready"
    assert len(calls) == 2


def test_gives_up_after_three_attempts(monkeypatch):
    calls = install(monkeypatch, [fake_response(500), fake_response(500), fake_response(500)])
    with pytest.raises(LlmError) as error:
        make_provider().complete("s", "u")
    assert error.value.status_code == 502
    assert len(calls) == 3


def test_a_rejected_key_is_not_retried(monkeypatch):
    calls = install(monkeypatch, [fake_response(401)])
    with pytest.raises(LlmError):
        make_provider().complete("s", "u")
    assert len(calls) == 1


def test_a_rate_limit_is_retried_and_reported_as_busy(monkeypatch):
    calls = install(monkeypatch, [fake_response(429), fake_response(429), fake_response(429)])
    with pytest.raises(LlmError) as error:
        make_provider().complete("s", "u")
    assert error.value.status_code == 503
    assert len(calls) == 3


def test_a_client_error_is_not_retried(monkeypatch):
    calls = install(monkeypatch, [fake_response(400)])
    with pytest.raises(LlmError):
        make_provider().complete("s", "u")
    assert len(calls) == 1