package com.example.social.server.integration;

import com.example.social.server.entity.Post;
import com.example.social.server.entity.UserRole;
import com.example.social.server.repository.PostEmbeddingRepository;
import com.example.social.server.repository.PostRepository;
import com.example.social.server.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Запити схожості постів на справжньому pgvector.
 * Ембединги штучні: одиничні вектори, тож очікувані значення відомі точно —
 * однаковий напрям → 1.0, перпендикулярні → 0.5, під кутом 45° → 0.5 + √0.5 / 2 ≈ 0.854
 * (схожість у проєкті = 1 − cosine_distance / 2).
 */
class PostEmbeddingRepositoryIntegrationTest extends IntegrationTestBase {

    private static final int DIMENSION = 768;
    private static final double DIAGONAL_45 = 0.5 + Math.sqrt(0.5) / 2;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostEmbeddingRepository embeddings;

    /** Пост з ембедингом, у якому одиниці стоять на вказаних координатах (без координат — без ембедингу). */
    private long post(TestUser author, int... hotDimensions) {
        Post post = new Post();
        post.setUser(userRepository.findById(author.id()).orElseThrow());
        post.setText("тестовий пост");
        post.setCreatedDate(LocalDateTime.now());
        long postId = postRepository.save(post).getPostId();

        if (hotDimensions.length > 0) {
            List<Double> vector = new ArrayList<>(Collections.nCopies(DIMENSION, 0.0));
            for (int d : hotDimensions) {
                vector.set(d, 1.0);
            }
            embeddings.saveEmbedding(postId, vector);
        }
        return postId;
    }

    @Test
    void averageSimilarityIsReturnedForEveryCandidate() {
        TestUser author = createUser(UserRole.user);
        long seed = post(author, 0);
        long same = post(author, 0);
        long orthogonal = post(author, 1);
        long diagonal = post(author, 0, 1);

        Map<Long, Double> result = embeddings.findAverageSemanticSimilarity(
                List.of(same, orthogonal, diagonal), List.of(seed));

        // кожен кандидат має отримати оцінку — жоден рядок результату не губиться
        assertEquals(Set.of(same, orthogonal, diagonal), result.keySet());
        assertEquals(1.0, result.get(same), 1e-5);
        assertEquals(0.5, result.get(orthogonal), 1e-5);
        assertEquals(DIAGONAL_45, result.get(diagonal), 1e-5);
    }

    @Test
    void pairwiseMatrixIsSymmetricWithOnesOnDiagonal() {
        TestUser author = createUser(UserRole.user);
        long a = post(author, 0);
        long aCopy = post(author, 0);
        long b = post(author, 1);
        long noEmbedding = post(author);

        double[][] m = embeddings.findPairwiseSimilarity(List.of(a, aCopy, b, noEmbedding));

        assertEquals(1.0, m[0][1], 1e-5);
        assertEquals(1.0, m[1][0], 1e-5);
        assertEquals(0.5, m[0][2], 1e-5);
        assertEquals(0.5, m[2][1], 1e-5);
        assertEquals(0.0, m[3][0], 1e-12);   // без ембедингу — вважається «іншим»
        for (int i = 0; i < 4; i++) {
            assertEquals(1.0, m[i][i], 1e-12);
        }
    }
}