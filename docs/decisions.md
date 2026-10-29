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