package com.example.social.server.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.util.ArrayList;
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

    /** Пост і його косинусна схожість із запитом: 1 — той самий зміст, 0 — не пов'язані. */
    public record Neighbour(long postId, double similarity) {
    }

    /**
     * Найближчі за змістом пости до вектора запиту (пошук за змістом, підказки тегів).
     * Повертає не більше limit постів, схожість яких щонайменше minSimilarity.
     */
    public List<Neighbour> findNearest(List<Double> vector, int limit, double minSimilarity) {
        String vectorLiteral = toVectorLiteral(vector);
        // відстань <=> рахується один раз у підзапиті; ORDER BY по ній використовує індекс HNSW (V16)
        return jdbcTemplate.query(
                "SELECT post_id, 1 - distance AS similarity FROM (" +
                        "  SELECT post_id, embedding <=> ?::vector AS distance FROM posts " +
                        "  WHERE embedding IS NOT NULL ORDER BY embedding <=> ?::vector LIMIT ?" +
                        ") nearest WHERE 1 - distance >= ? ORDER BY distance",
                (rs, i) -> new Neighbour(rs.getLong("post_id"), rs.getDouble("similarity")),
                vectorLiteral, vectorLiteral, limit, minSimilarity);
    }

    /** Найближчі за змістом пости до вже збереженого поста (без нього самого). */
    public List<Neighbour> findNearestToPost(long postId, int limit) {
        return jdbcTemplate.query(
                "SELECT p.post_id, 1 - (p.embedding <=> s.embedding) AS similarity " +
                        "FROM posts s JOIN posts p ON p.post_id <> s.post_id " +
                        "WHERE s.post_id = ? AND s.embedding IS NOT NULL AND p.embedding IS NOT NULL " +
                        "ORDER BY p.embedding <=> s.embedding LIMIT ?",
                (rs, i) -> new Neighbour(rs.getLong("post_id"), rs.getDouble("similarity")),
                postId, limit);
    }

    /** postId → назви його тегів (для постів зі списку; пости без тегів не потрапляють у результат). */
    public Map<Long, List<String>> findTagsOf(List<Long> postIds) {
        Map<Long, List<String>> result = new HashMap<>();
        if (postIds.isEmpty()) {
            return result;
        }
        Long[] idArray = postIds.toArray(new Long[0]);
        jdbcTemplate.query(
                "SELECT pt.post_id, t.name FROM post_tags pt JOIN tags t ON t.tag_id = pt.tag_id " +
                        "WHERE pt.post_id = ANY(?) ORDER BY pt.post_id, t.name",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("bigint", idArray)),
                (RowCallbackHandler) rs -> result.computeIfAbsent(rs.getLong("post_id"), x -> new ArrayList<>())
                        .add(rs.getString("name")));
        return result;
    }

    /** postId → id автора. */
    public Map<Long, Long> findAuthorsOf(List<Long> postIds) {
        Map<Long, Long> result = new HashMap<>();
        if (postIds.isEmpty()) {
            return result;
        }
        Long[] idArray = postIds.toArray(new Long[0]);
        jdbcTemplate.query(
                "SELECT post_id, user_id FROM posts WHERE post_id = ANY(?)",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("bigint", idArray)),
                (RowCallbackHandler) rs -> result.put(rs.getLong("post_id"), rs.getLong("user_id")));
        return result;
    }

    /** Id усіх постів, що мають вектор і хоча б один тег (для перевірки якості векторів). */
    public List<Long> findTaggedPostIdsWithEmbedding() {
        return jdbcTemplate.queryForList(
                "SELECT p.post_id FROM posts p WHERE p.embedding IS NOT NULL " +
                        "AND EXISTS (SELECT 1 FROM post_tags pt WHERE pt.post_id = p.post_id) ORDER BY p.post_id",
                Long.class);
    }

    /**
     * Для кожного поста з postIds — k найближчих за вектором постів серед тих, що мають тег
     * (один запит із LATERAL-підзапитом замість окремого запиту на кожен пост).
     */
    public Map<Long, List<Long>> findNearestForEach(List<Long> postIds, int k) {
        Map<Long, List<Long>> result = new HashMap<>();
        if (postIds.isEmpty()) {
            return result;
        }
        Long[] idArray = postIds.toArray(new Long[0]);
        String sql = "SELECT s.post_id AS source_id, n.post_id AS neighbour_id FROM posts s " +
                "CROSS JOIN LATERAL (" +
                "  SELECT p.post_id FROM posts p WHERE p.post_id <> s.post_id AND p.embedding IS NOT NULL " +
                "  AND EXISTS (SELECT 1 FROM post_tags pt WHERE pt.post_id = p.post_id) " +
                "  ORDER BY p.embedding <=> s.embedding LIMIT ?" +
                ") n WHERE s.post_id = ANY(?) AND s.embedding IS NOT NULL";
        jdbcTemplate.query(
                sql,
                ps -> {
                    ps.setInt(1, k);
                    ps.setArray(2, ps.getConnection().createArrayOf("bigint", idArray));
                },
                (RowCallbackHandler) rs -> result.computeIfAbsent(rs.getLong("source_id"), x -> new ArrayList<>())
                        .add(rs.getLong("neighbour_id")));
        return result;
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