# Decisions log

## 2026-10-28: PostgreSQL + pgvector instead of a separate vector database
- Why: one database for users, documents and vectors, so one system to run, back up and secure
- Trade-off: dedicated vector databases may scale better at very large sizes, which this project does not need

## 2026-10-28: Vector size 384
- Why: matches the embedding model bge-small-en-v1.5
- Consequence: changing the embedding model later means re-embedding every chunk

## 2026-10-28: Generated file names for uploads
- Why: avoids path-traversal and name-collision problems from user-supplied names

## 2026-10-28: No vector index yet
- Why: Phase 3 compares exact search with an HNSW index, so the table starts unindexed


## 2026-10-29: Hibernate validates the schema instead of creating it
- Why: the SQL file is the single source of truth, and a mismatch stops the app at startup instead of failing later
- Trade-off: every schema change needs a SQL change and a matching entity change

## 2026-10-29: Documents are always loaded by id and owner together
- Why: a user guessing another user's document id gets "not found", never someone else's file


## 2026-10-29: Stateless JWT authentication
- Why: no server-side sessions to store or scale, and it fits a separate React frontend
- Trade-off: a token cannot be revoked before it expires, so tokens are short-lived (1 hour)

## 2026-10-29: BCrypt for passwords, with a 72-character limit
- Why: slow, salted, one-way hashing; BCrypt only reads 72 bytes, so the API enforces it

## 2026-10-29: Same error for unknown email and wrong password
- Why: stops attackers from using the login to discover which emails are registered

## 2026-10-29: CSRF protection disabled
- Why: no cookies or sessions are used, only an Authorization header


## 2026-10-29: Files are validated by their first bytes, not by name or label
- Why: the file name and Content-Type are chosen by the client, so they can lie
- Consequence: only PDF, PNG and JPEG are accepted, and the stored extension comes from the detected type

## 2026-10-29: Uploaded files are stored under random UUID names
- Why: removes path traversal and name collision risks; the original name is kept in the database for display only

## 2026-10-29: Another user's document returns 404, not 403
- Why: a 403 would confirm that the id exists, and a 404 reveals nothing

## 2026-10-29: Document.createdAt is set in Java
- Why: Hibernate does not read back database-generated values after an insert, so the API response would show null


## 2026-10-30: Files are validated by their first bytes, not by name or label
- Why: the file name and Content-Type are chosen by the client, so they can lie
- Consequence: only PDF, PNG and JPEG are accepted, and the stored extension comes from the detected type

## 2026-10-30: Uploaded files are stored under random UUID names
- Why: removes path traversal and name collision risks; the original name is kept in the database for display only

## 2026-10-30: Another user's document returns 404, not 403
- Why: a 403 would confirm that the id exists, and a 404 reveals nothing

## 2026-10-30: Document.createdAt is set in Java
- Why: Hibernate does not read back database-generated values after an insert, so the API response would show null


## 2026-10-30: Separate Python service for AI work
- Why: Python has the strongest PDF, OCR and embedding libraries; Spring Boot keeps users, files and the database
- Trade-off: two services to run and deploy, connected over HTTP

## 2026-10-30: The AI service is stateless and receives the file over HTTP
- Why: easy to test, easy to run in Docker, and it does not share a disk with the backend

## 2026-10-30: PyMuPDF for text extraction, page by page
- Why: fast and accurate for PDFs with real text; page numbers are kept for citations
- Pages with almost no text are flagged for OCR

## 2026-10-30: requirements.txt created with pip freeze
- Why: it records the exact versions that worked, so the service rebuilds the same way anywhere