from fastapi.testclient import TestClient

import app.main as main_module
from app.llm import LlmError
from app.rag import build_user_prompt, parse_answer

CONTEXTS = [
    {"n": 1, "document": "notes.pdf", "page": 6, "text": "A candidate key is a minimal super key."},
    {"n": 2, "document": "notes.pdf", "page": 9, "text": "ACID stands for four properties."},
]


def test_not_found_marker_is_detected():
    assert parse_answer("NOT_FOUND", {1, 2}) == {"answer": "", "not_found": True, "cited": []}
    assert parse_answer("not_found.", {1, 2})["not_found"] is True
    assert parse_answer("   ", {1, 2})["not_found"] is True


def test_valid_citations_are_kept_and_sorted():
    result = parse_answer("A key [2] is useful [1][2].", {1, 2})
    assert result["cited"] == [1, 2]
    assert result["not_found"] is False


def test_invented_citations_are_removed():
    result = parse_answer("Fact one [1]. Fact two [9].", {1, 2})
    assert "[9]" not in result["answer"]
    assert result["cited"] == [1]


def test_thinking_blocks_are_removed():
    result = parse_answer("<think>hmm</think>The answer [1].", {1})
    assert result["answer"] == "The answer [1]."


def test_prompt_numbers_the_passages_and_names_the_source():
    prompt = build_user_prompt("What is a key?", CONTEXTS)
    assert "[1] (source: notes.pdf, page 6)" in prompt
    assert "[2] (source: notes.pdf, page 9)" in prompt
    assert "Question: What is a key?" in prompt


class FakeProvider:
    def __init__(self, reply):
        self.reply = reply

    def complete(self, system, user):
        return self.reply


def test_answer_endpoint_with_a_fake_model(monkeypatch):
    monkeypatch.setattr(main_module, "get_provider", lambda: FakeProvider("A candidate key is minimal [1]."))
    client = TestClient(main_module.app)
    response = client.post("/answer", json={"question": "What is a candidate key?", "contexts": CONTEXTS})
    assert response.status_code == 200
    assert response.json()["cited"] == [1]
    assert response.json()["not_found"] is False


class BrokenProvider:
    def complete(self, system, user):
        raise LlmError("The language model took too long to answer", 504)


def test_answer_endpoint_reports_model_failure(monkeypatch):
    monkeypatch.setattr(main_module, "get_provider", lambda: BrokenProvider())
    client = TestClient(main_module.app)
    response = client.post("/answer", json={"question": "Q?", "contexts": CONTEXTS})
    assert response.status_code == 504


def test_answer_endpoint_rejects_empty_context():
    client = TestClient(main_module.app)
    response = client.post("/answer", json={"question": "Q?", "contexts": []})
    assert response.status_code == 422