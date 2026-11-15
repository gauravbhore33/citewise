import os
from pathlib import Path

from dotenv import load_dotenv

# the project's single .env file is in the repository root, one level above ai-service
load_dotenv(Path(__file__).resolve().parents[2] / ".env", override=False)

LLM_BASE_URL = os.getenv("LLM_BASE_URL", "").rstrip("/")
LLM_MODEL = os.getenv("LLM_MODEL", "")
LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_TIMEOUT_SECONDS = float(os.getenv("LLM_TIMEOUT_SECONDS", "60"))
LLM_MAX_TOKENS = int(os.getenv("LLM_MAX_TOKENS", "700"))