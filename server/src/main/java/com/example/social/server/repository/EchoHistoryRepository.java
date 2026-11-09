package com.example.social.server.repository;

import com.example.social.shared.dto.EchoSnapshotDto;
import com.example.social.shared.dto.UserEchoPointDto;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Історія ехо-камер (V8). Через JdbcTemplate, а не JPA: member_ids — масив bigint[],
 * а запит траєкторії користувача використовує оператор масивів @> з GIN-індексом.
 */
@Repository
public class EchoHistoryRepository {

    /** Спільнота в знімку разом зі складом. */
    public record CommunityRow(long community, int memberCount, int postCount,
                               double topicHomogeneity, Double semanticCohesion,
                               double avgSentiment, double sentimentHomogeneity, double echoChamberScore,
                               List<Long> memberIds) {
    }

    private static final String SNAPSHOT_SELECT =
            "SELECT s.snapshot_id, s.algorithm, s.source, s.community_count, s.user_count, " +
                    "s.weighted_echo_score, s.max_echo_score, u.username AS created_by, s.created_at " +
                    "FROM echo_snapshot s LEFT JOIN \"user\" u ON u.user_id = s.created_by ";

    private static final RowMapper<EchoSnapshotDto> SNAPSHOT = (rs, rowNum) -> new EchoSnapshotDto(
            rs.getLong("snapshot_id"),
            rs.getString("algorithm"),
            rs.getString("source"),
            rs.getInt("community_count"),
            rs.getInt("user_count"),
            rs.getDouble("weighted_echo_score"),
            rs.getDouble("max_echo_score"),
            rs.getString("created_by"),
            rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbcTemplate;

    public EchoHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @return id нового знімка */
    public long insertSnapshot(String algorithm, String source, Long createdBy,
                               int communityCount, int userCount,
                               double weightedEchoScore, double maxEchoScore) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO echo_snapshot (algorithm, source, created_by, community_count, user_count, " +
                        "weighted_echo_score, max_echo_score) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING snapshot_id",
                Long.class,
                algorithm, source, createdBy, communityCount, userCount, weightedEchoScore, maxEchoScore);
        return id;
    }

    public void insertCommunities(long snapshotId, List<CommunityRow> rows) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO echo_snapshot_community (snapshot_id, community, member_count, post_count, " +
                        "topic_homogeneity, semantic_cohesion, avg_sentiment, sentiment_homogeneity, " +
                        "echo_chamber_score, member_ids) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        CommunityRow row = rows.get(i);
                        ps.setLong(1, snapshotId);
                        ps.setLong(2, row.community());
                        ps.setInt(3, row.memberCount());
                        ps.setInt(4, row.postCount());
                        ps.setDouble(5, row.topicHomogeneity());
                        ps.setObject(6, row.semanticCohesion(), Types.DOUBLE);   // може бути null
                        ps.setDouble(7, row.avgSentiment());
                        ps.setDouble(8, row.sentimentHomogeneity());
                        ps.setDouble(9, row.echoChamberScore());
                        ps.setArray(10, ps.getConnection().createArrayOf("bigint", row.memberIds().toArray()));
                    }

                    @Override
                    public int getBatchSize() {
                        return rows.size();
                    }
                });
    }

    /** Останні limit знімків алгоритму — від найстарішого до найновішого (зручно для графіка). */
    public List<EchoSnapshotDto> findSnapshots(String algorithm, int limit) {
        List<EchoSnapshotDto> newestFirst = jdbcTemplate.query(
                SNAPSHOT_SELECT + "WHERE s.algorithm = ? ORDER BY s.snapshot_id DESC LIMIT ?",
                SNAPSHOT, algorithm, limit);
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    public Optional<EchoSnapshotDto> findSnapshot(long snapshotId) {
        return jdbcTemplate.query(SNAPSHOT_SELECT + "WHERE s.snapshot_id = ?", SNAPSHOT, snapshotId)
                .stream().findFirst();
    }

    public Optional<EchoSnapshotDto> findLatest(String algorithm) {
        return jdbcTemplate.query(
                        SNAPSHOT_SELECT + "WHERE s.algorithm = ? ORDER BY s.snapshot_id DESC LIMIT 1",
                        SNAPSHOT, algorithm)
                .stream().findFirst();
    }

    /** Попередній знімок того самого алгоритму. */
    public Optional<EchoSnapshotDto> findPrevious(String algorithm, long snapshotId) {
        return jdbcTemplate.query(
                        SNAPSHOT_SELECT + "WHERE s.algorithm = ? AND s.snapshot_id < ? " +
                                "ORDER BY s.snapshot_id DESC LIMIT 1",
                        SNAPSHOT, algorithm, snapshotId)
                .stream().findFirst();
    }

    /** Спільноти знімка, від найвищого echo-score. */
    public List<CommunityRow> findCommunities(long snapshotId) {
        return jdbcTemplate.query(
                "SELECT * FROM echo_snapshot_community WHERE snapshot_id = ? " +
                        "ORDER BY echo_chamber_score DESC, community",
                (rs, rowNum) -> new CommunityRow(
                        rs.getLong("community"),
                        rs.getInt("member_count"),
                        rs.getInt("post_count"),
                        rs.getDouble("topic_homogeneity"),
                        rs.getObject("semantic_cohesion", Double.class),
                        rs.getDouble("avg_sentiment"),
                        rs.getDouble("sentiment_homogeneity"),
                        rs.getDouble("echo_chamber_score"),
                        Arrays.asList((Long[]) rs.getArray("member_ids").getArray())),
                snapshotId);
    }

    /** У якій спільноті був користувач у кожному з останніх limit знімків — від найстарішого. */
    public List<UserEchoPointDto> findUserTrajectory(long userId, String algorithm, int limit) {
        List<UserEchoPointDto> newestFirst = jdbcTemplate.query(
                "SELECT s.snapshot_id, s.created_at, s.weighted_echo_score, " +
                        "c.community, c.member_count, c.echo_chamber_score " +
                        "FROM echo_snapshot_community c JOIN echo_snapshot s ON s.snapshot_id = c.snapshot_id " +
                        "WHERE s.algorithm = ? AND c.member_ids @> ARRAY[?]::bigint[] " +
                        "ORDER BY s.snapshot_id DESC LIMIT ?",
                (rs, rowNum) -> new UserEchoPointDto(
                        rs.getLong("snapshot_id"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getLong("community"),
                        rs.getInt("member_count"),
                        rs.getDouble("echo_chamber_score"),
                        rs.getDouble("weighted_echo_score")),
                algorithm, userId, limit);
        Collections.reverse(newestFirst);
        return newestFirst;
    }
}