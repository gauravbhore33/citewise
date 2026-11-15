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


## 2026-10-31: Tesseract OCR only for pages with no text layer
- Why: embedded text is exact and fast; OCR is slow and approximate, so it is a fallback
- Each page records how it was read ("text" or "ocr"), because OCR quality may matter in evaluation

## 2026-10-31: PDF pages are rendered at 300 DPI in grayscale before OCR
- Why: a common balance of accuracy and speed; configurable with OCR_DPI

## 2026-10-31: Limits on pages (300) and OCR time (60 seconds per page)
- Why: a huge or strange file must not be able to tie up the service


## 2026-11-01: Text extraction runs in the background
- Why: OCR can take minutes; the upload returns at once and the document status shows progress (UPLOADED, PROCESSING, READY, FAILED)
- Limit: two worker threads and a queue of 50, so a burst of uploads cannot overload the server

## 2026-11-01: No database transaction is held during the call to the AI service
- Why: a transaction open for minutes would hold a database connection; two short transactions are used instead

## 2026-11-01: Failures are stored as short, safe messages
- Why: users see what went wrong ("service not reachable"); technical details go only to the log

## 2026-11-01: Reprocessing deletes old pages first
- Why: it makes the operation safe to repeat, and gives a recovery path for failed documents

## 2026-11-01: Schema change applied by hand to the existing database
- The new SQL file also runs on fresh databases
- Future improvement: a migration tool such as Flyway


## 2026-11-01: Chunks of about 200 words with 40 words of overlap, never crossing a page
- Why: 200 words is around 300 tokens, safely under the embedding model's 512-token limit; overlap protects ideas cut by a boundary
- Chunks stay inside one page so every citation points at an exact page
- Trade-off: an idea split across two pages becomes two chunks
- These are starting values; Phase 7 compares other sizes using the evaluation set

## 2026-11-01: Chunking is written by hand, without a framework
- Why: the logic is short, fully testable, and I can explain every line

## 2026-11-01: Chunk boundaries snap to sentence ends
- Why: fewer chunks that start or stop in the middle of a sentence

## 2026-11-01: Text cleaning drops lines with no letters and long decoration runs
- Why: page numbers and dotted lines add noise to embeddings
- Trade-off: lone code braces and number-only table lines are lost


## 2026-11-01: Chunks and vectors are written with plain SQL (JdbcTemplate), not a JPA entity
- Why: Hibernate does not map the pgvector column cleanly; the SQL file stays the single source of truth
- Trade-off: this one table is outside the entity model

## 2026-11-01: A document becomes READY only after its vectors are stored
- Why: the chunk replacement and the status change happen in one transaction, so search never sees a half-written document

## 2026-11-01: Search filters by user inside the same SQL query as the ranking
- Why: filtering after fetching the top results could leak other users' text or hide the user's own matches

## 2026-11-01: Search starts as exact (no index); the HNSW index comes later
- Why: exact search is the baseline for measuring recall and speed of the approximate index

## 2026-11-01: The API returns both similarity and distance
- Similarity (1 minus cosine distance) reads more naturally; the raw distance matches what pgvector reports


## 2026-11-01: Two layers of refusal
- Layer 1: if the best retrieved chunk scores below a threshold, refuse without calling the model (cheap, cannot hallucinate)
- Layer 2: the prompt tells the model to answer NOT_FOUND when the passages do not contain the answer, which catches questions on the right topic that the notes do not cover
- The threshold is chosen from calibration and the evaluation set, not guessed

## 2026-11-01: Citations are validated, and sources come from the database
- The model cites numbered passages; any number that was not sent is deleted from the answer
- The sources list is built from stored chunk rows, never from model-written text, so a source cannot be invented

## 2026-11-01: Retrieved passages are treated as untrusted data
- Why: a document could contain instructions (prompt injection); the prompt says to ignore them

## 2026-11-01: One provider class for any OpenAI-compatible API
- Why: Groq, OpenAI and local Ollama all speak the same protocol, so switching is a change in .env
- The API key is read from the environment, sent only in the request header, and never logged

## 2026-11-01: Temperature 0 for answers
- Why: factual answers from sources should be repeatable


## 2026-11-14: Starting layer-1 threshold is 0.50 similarity
- Measured on 25 questions: answerable 0.586 to 0.923, off-topic up to 0.566, related-but-absent up to 0.674
- A single threshold cannot separate related-but-absent questions from answerable ones, so layer 2 (the model's NOT_FOUND) is required
- 0.50 never refused an answerable question in the sample; false refusals cost more than extra model calls
- To be tuned with the Phase 6 evaluation set

## 2026-11-14: The citation parser accepts the model's own bracket styles
- The model wrote 【1†L1-L3】 instead of [1]; the parser normalises it and still deletes numbers that were not sent
- The prompt also asks for plain [n] citations

## 2026-11-14: Example files contain placeholders only
- GitHub push protection blocked a push because a real API key was in .env.example
- The key was revoked and replaced, and the commit was rewritten before any push


## 2026-11-15: The evaluation judges retrieval by phrases, not page numbers
- Each answerable question lists short phrases found only in the answering text
- Why: page numbers change when a file is re-uploaded, and phrases are chosen so the question-list pages do not match

## 2026-11-15: 30 questions, one-third unanswerable
- 20 answerable (3 paraphrased), 5 related-but-absent, 5 off-topic
- Metrics: hit@1/3/5, MRR, false refusals, false answers, citation support, refusals by layer

## 2026-11-15: Tuning and testing on the same questions overstates the result
- Plan: add about 10 fresh questions at the end and report them separately as a held-out check

## 2026-11-15: Observation, question-list pages outrank answer pages
- The notes end with a list of interview questions; questions embed close to other questions
- ACID, LEFT JOIN and first normal form all returned list pages first
- Phase 7 experiment: down-rank or exclude question-list chunks, and compare hybrid keyword plus vector search