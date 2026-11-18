FROM python:3.14-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    MODEL_CACHE_DIR=/models

# the OCR engine (English language data included)
# use https, and retry: the plain-http Debian servers timed out on this network
RUN find /etc/apt -type f \( -name "*.list" -o -name "*.sources" \) -exec sed -i 's|http://|https://|g' {} + \
    && apt-get -o Acquire::Retries=5 update \
    && apt-get -o Acquire::Retries=5 install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir --retries 5 --default-timeout 60 -r requirements.txt

COPY app app

# download the embedding model into the image, so the first question is not slow
RUN python -c "from fastembed import TextEmbedding; TextEmbedding(model_name='BAAI/bge-small-en-v1.5', cache_dir='/models')"

EXPOSE 8000
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]