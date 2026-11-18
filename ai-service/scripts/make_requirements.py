from importlib.metadata import version
from pathlib import Path

# the libraries our code imports directly; pip finds the rest by itself
PACKAGES = [
    "fastapi",
    "uvicorn[standard]",
    "python-multipart",
    "pymupdf",
    "pytesseract",
    "pillow",
    "fastembed",
    "numpy",
    "httpx",
    "python-dotenv",
]

lines = []
for name in PACKAGES:
    plain = name.split("[")[0]
    lines.append(f"{name}=={version(plain)}")

target = Path(__file__).resolve().parents[1] / "requirements.txt"
target.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"Wrote {target}\n")
print("\n".join(lines))