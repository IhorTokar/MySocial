package com.example.social.server.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class PostEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostEmbeddingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveEmbedding(Long postId, List<Double> vector) {
        String vectorLiteral = toVectorLiteral(vector);
        jdbcTemplate.update(
                "UPDATE posts SET embedding = ?::vector WHERE post_id = ?",
                vectorLiteral, postId
        );
    }

    /**
     * Топ-N постів, семантично найближчих до заданого вектора (косинусна відстань).
     * excludePostId — щоб не рекомендувати пост самому собі, якщо він у seed-наборі.
     */
    public List<Long> findSimilarPostIds(List<Double> vector, int topN, Long excludePostId) {
        String vectorLiteral = toVectorLiteral(vector);
        return jdbcTemplate.queryForList(
                "SELECT post_id FROM posts " +
                        "WHERE embedding IS NOT NULL AND post_id != ? " +
                        "ORDER BY embedding <=> ?::vector " +
                        "LIMIT ?",
                Long.class,
                excludePostId, vectorLiteral, topN
        );
    }

    /**
     * Косинусна відстань (0 = ідентичні, 2 = протилежні; для нормалізованих
     * векторів 1 - (distance/2) дає схожість у діапазоні [0,1]) між двома
     * конкретними постами — потрібно для рахунку semanticScore у RecommendationService.
     */
    public Double cosineDistance(Long postIdA, Long postIdB) {
        return jdbcTemplate.queryForObject(
                "SELECT a.embedding <=> b.embedding " +
                        "FROM posts a, posts b " +
                        "WHERE a.post_id = ? AND b.post_id = ? " +
                        "AND a.embedding IS NOT NULL AND b.embedding IS NOT NULL",
                Double.class,
                postIdA, postIdB
        );
    }

    private String toVectorLiteral(List<Double> vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(vector.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Для кожного посту з candidatePostIds рахує середню косинусну схожість
     * до seedPostIds (постів, з якими юзер взаємодіяв). Повертає лише пости,
     * що мають embedding і хоча б один seed-пост теж має embedding.
     * Similarity = 1 - (cosine_distance / 2), в діапазоні [0, 1] для нормалізованих векторів.
     */
    public Map<Long, Double> findAverageSemanticSimilarity(List<Long> candidatePostIds, List<Long> seedPostIds) {
        if (candidatePostIds.isEmpty() || seedPostIds.isEmpty()) {
            return Map.of();
        }

        String sql = "SELECT c.post_id AS candidate_id, " +
                "AVG(1 - (c.embedding <=> s.embedding) / 2) AS avg_similarity " +
                "FROM posts c, posts s " +
                "WHERE c.post_id = ANY(?) AND s.post_id = ANY(?) " +
                "AND c.embedding IS NOT NULL AND s.embedding IS NOT NULL " +
                "GROUP BY c.post_id";

        Long[] candidateArray = candidatePostIds.toArray(new Long[0]);
        Long[] seedArray = seedPostIds.toArray(new Long[0]);

        Map<Long, Double> result = new HashMap<>();
        jdbcTemplate.query(
                sql,
                ps -> {
                    ps.setArray(1, ps.getConnection().createArrayOf("bigint", candidateArray));
                    ps.setArray(2, ps.getConnection().createArrayOf("bigint", seedArray));
                },
                // RowCallbackHandler викликається для кожного рядка окремо; власний while (rs.next())
                // тут пропускав би перший рядок результату
                (RowCallbackHandler) rs -> result.put(rs.getLong("candidate_id"), rs.getDouble("avg_similarity"))
        );

        return result;
    }

    /**
     * Середня попарна косинусна близькість між усіма постами з набору —
     * міра "семантичної згуртованості" групи (наприклад, спільноти).
     * a.post_id < b.post_id уникає подвійного підрахунку пари та порівняння поста з самим собою.
     */
    public Double findAveragePairwiseSimilarity(List<Long> postIds) {
        if (postIds.size() < 2) {
            return null;
        }

        String sql = "SELECT AVG(1 - (a.embedding <=> b.embedding) / 2) AS avg_similarity " +
                "FROM posts a JOIN posts b ON a.post_id < b.post_id " +
                "WHERE a.post_id = ANY(?) AND b.post_id = ANY(?) " +
                "AND a.embedding IS NOT NULL AND b.embedding IS NOT NULL";

        Long[] idArray = postIds.toArray(new Long[0]);

        return jdbcTemplate.query(
                sql,
                ps -> {
                    ps.setArray(1, ps.getConnection().createArrayOf("bigint", idArray));
                    ps.setArray(2, ps.getConnection().createArrayOf("bigint", idArray));
                },
                rs -> rs.next() ? rs.getObject("avg_similarity", Double.class) : null
        );
    }

    /**
     * Матриця попарної схожості постів у тому ж порядку, що postIds:
     * similarity = 1 − cosine_distance / 2 ∈ [0, 1], на діагоналі 1.
     * Якщо в поста немає ембедингу, його схожість з іншими лишається 0 (вважається «іншим»).
     * Потрібна для MMR-перестановки стрічки.
     */
    public double[][] findPairwiseSimilarity(List<Long> postIds) {
        int n = postIds.size();
        double[][] matrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            matrix[i][i] = 1.0;
        }
        if (n < 2) {
            return matrix;
        }

        Map<Long, Integer> indexById = new HashMap<>();
        for (int i = 0; i < n; i++) {
            indexById.put(postIds.get(i), i);
        }

        String sql = "SELECT a.post_id AS a_id, b.post_id AS b_id, " +
                "1 - (a.embedding <=> b.embedding) / 2 AS similarity " +
                "FROM posts a JOIN posts b ON a.post_id < b.post_id " +
                "WHERE a.post_id = ANY(?) AND b.post_id = ANY(?) " +
                "AND a.embedding IS NOT NULL AND b.embedding IS NOT NULL";
        Long[] idArray = postIds.toArray(new Long[0]);

        // RowCallbackHandler викликається для КОЖНОГО рядка — тому всередині без while (rs.next())
        RowCallbackHandler fillCell = rs -> {
            int i = indexById.get(rs.getLong("a_id"));
            int j = indexById.get(rs.getLong("b_id"));
            double similarity = rs.getDouble("similarity");
            matrix[i][j] = similarity;
            matrix[j][i] = similarity;
        };
        jdbcTemplate.query(
                sql,
                ps -> {
                    ps.setArray(1, ps.getConnection().createArrayOf("bigint", idArray));
                    ps.setArray(2, ps.getConnection().createArrayOf("bigint", idArray));
                },
                fillCell);
        return matrix;
    }

}