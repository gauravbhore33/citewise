package com.citewise.backend.repository;

import java.sql.Types;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ChatRepository {

    public record SourceRow(int n, Long documentId, String documentName, int pageNumber,
                            double similarity, boolean cited, String content) {}

    public record MessageRow(long id, String question, String answer, String status, String reason,
                             Double topSimilarity, String model, Instant createdAt) {}

    private record SourceWithMessage(long messageId, SourceRow row) {}

    private final JdbcTemplate jdbc;

    public ChatRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Saves a question, its answer and the sources used, all or nothing. Returns the new message id. */
    @Transactional
    public long saveMessage(long userId, String question, String answer, String status, String reason,
                            Double topSimilarity, String model, List<SourceRow> sources) {
        Long id = jdbc.queryForObject(
                "INSERT INTO chat_messages (user_id, question, answer, status, reason, top_similarity, model) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class,
                userId, question, answer, status, reason, topSimilarity, model);

        if (!sources.isEmpty()) {
            jdbc.batchUpdate(
                    "INSERT INTO chat_sources (message_id, n, document_id, document_name, page_number, similarity, cited, content) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    sources, 50,
                    (ps, s) -> {
                        ps.setLong(1, id);
                        ps.setInt(2, s.n());
                        if (s.documentId() == null) {
                            ps.setNull(3, Types.BIGINT);
                        } else {
                            ps.setLong(3, s.documentId());
                        }
                        ps.setString(4, s.documentName());
                        ps.setInt(5, s.pageNumber());
                        ps.setDouble(6, s.similarity());
                        ps.setBoolean(7, s.cited());
                        ps.setString(8, s.content());
                    });
        }
        return id;
    }

    /** The user's most recent saved answers and refusals, newest first. */
    public List<MessageRow> history(long userId, int limit) {
        return jdbc.query(
                "SELECT id, question, answer, status, reason, top_similarity, model, created_at "
                        + "FROM chat_messages WHERE user_id = ? AND status <> 'ERROR' "
                        + "ORDER BY created_at DESC, id DESC LIMIT ?",
                (rs, rowNum) -> new MessageRow(
                        rs.getLong("id"),
                        rs.getString("question"),
                        rs.getString("answer"),
                        rs.getString("status"),
                        rs.getString("reason"),
                        rs.getObject("top_similarity", Double.class),
                        rs.getString("model"),
                        rs.getTimestamp("created_at").toInstant()),
                userId, limit);
    }

    public Map<Long, List<SourceRow>> sourcesFor(List<Long> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(messageIds.size(), "?"));
        List<SourceWithMessage> rows = jdbc.query(
                "SELECT message_id, n, document_id, document_name, page_number, similarity, cited, content "
                        + "FROM chat_sources WHERE message_id IN (" + placeholders + ") ORDER BY message_id, n",
                (rs, rowNum) -> new SourceWithMessage(
                        rs.getLong("message_id"),
                        new SourceRow(
                                rs.getInt("n"),
                                rs.getObject("document_id", Long.class),
                                rs.getString("document_name"),
                                rs.getInt("page_number"),
                                rs.getDouble("similarity"),
                                rs.getBoolean("cited"),
                                rs.getString("content"))),
                messageIds.toArray());

        return rows.stream().collect(Collectors.groupingBy(
                SourceWithMessage::messageId,
                Collectors.mapping(SourceWithMessage::row, Collectors.toList())));
    }
}