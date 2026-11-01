import os
from functools import lru_cache

from fastembed import TextEmbedding

MODEL_NAME = os.getenv("EMBEDDING_MODEL", "BAAI/bge-small-en-v1.5")
EMBEDDING_DIM = 384
BATCH_SIZE = 32

# The model files are stored here after the first download
_DEFAULT_CACHE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "model_cache")
CACHE_DIR = os.getenv("MODEL_CACHE_DIR", _DEFAULT_CACHE)

# bge models search best when a question starts with this instruction (stored text gets none)
QUERY_PREFIX = "Represent this sentence for searching relevant passages: "


@lru_cache(maxsize=1)
def get_model() -> TextEmbedding:
    """Load the model once and reuse it. The first call downloads it, so it is slow."""
    return TextEmbedding(model_name=MODEL_NAME, cache_dir=CACHE_DIR)


def embed_passages(texts: list[str]) -> list[list[float]]:
    """Turn stored text (chunks) into vectors."""
    return [vector.tolist() for vector in get_model().embed(texts, batch_size=BATCH_SIZE)]


def embed_query(text: str) -> list[float]:
    """Turn a user's question into a vector, with the search instruction in front."""
    return embed_passages([QUERY_PREFIX + text])[0]