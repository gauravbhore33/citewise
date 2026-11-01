package com.citewise.backend.repository;

import java.util.List;
import java.util.StringJoiner;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChunkRepository {

    public record NewChunk(int pageNumber, int chunkIndex, String content, List<Double> embedding) {}

    public record Hit(long chunkId, long documentId, String documentName,
                      int pageNumber, int chunkIndex, String content, double distance) {}

    private final JdbcTemplate jdbc;

    public ChunkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Replaces all chunks of a document. Call it inside a transaction so the swap is all-or-nothing. */
    public void replaceForDocument(long documentId, List<NewChunk> chunks) {
        jdbc.update("DELETE FROM chunks WHERE document_id = ?", documentId);

        jdbc.batchUpdate(
                "INSERT INTO chunks (document_id, page_number, chunk_index, content, source_type, embedding) "
                        + "VALUES (?, ?, ?, ?, 'TEXT', ?::vector)",
                chunks, 100,
                (ps, chunk) -> {
                    ps.setLong(1, documentId);
                    ps.setInt(2, chunk.pageNumber());
                    ps.setInt(3, chunk.chunkIndex());
                    ps.setString(4, chunk.content());
                    ps.setString(5, toVectorLiteral(chunk.embedding()));
                });
    }

    /** Exact nearest-neighbour search over one user's READY documents only. */
    public List<Hit> search(long userId, List<Double> queryVector, int topK) {
        String literal = toVectorLiteral(queryVector);
        return jdbc.query(
                "SELECT c.id, c.document_id, d.original_filename, c.page_number, c.chunk_index, c.content, "
                        + "c.embedding <=> ?::vector AS distance "
                        + "FROM chunks c JOIN documents d ON d.id = c.document_id "
                        + "WHERE d.user_id = ? AND d.status = 'READY' "
                        + "ORDER BY c.embedding <=> ?::vector "
                        + "LIMIT ?",
                (rs, rowNum) -> new Hit(
                        rs.getLong("id"),
                        rs.getLong("document_id"),
                        rs.getString("original_filename"),
                        rs.getInt("page_number"),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        rs.getDouble("distance")),
                literal, userId, literal, topK);
    }

    private static String toVectorLiteral(List<Double> vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (Double value : vector) {
            joiner.add(Double.toString(value));
        }
        return joiner.toString();
    }
}