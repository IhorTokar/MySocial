package com.example.social.server.repository;

import com.example.social.server.entity.Post;
import com.example.social.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByUserOrderByCreatedDateDesc(User user);
    List<Post> findByUserInOrderByCreatedDateDesc(List<User> users);
    List<Post> findTop50ByOrderByCreatedDateDesc();
    long countByUser(User user);
    List<Post> findByUser(User user);

    /** Пости з цього списку, для яких тональність ще не обчислено. */
    List<Post> findByPostIdInAndSentimentIsNull(Collection<Long> postIds);

    /** Для backfill: пости без тональності. */
    List<Post> findBySentimentIsNull();

    /** Для backfill: без тональності або з тональністю від вказаного джерела (наприклад, lexicon). */
    List<Post> findBySentimentIsNullOrSentimentSource(String sentimentSource);

    /** Вкладка «Відстежувані»: пости тих, кого читає користувач, від найновіших. */
    @Query(value = "SELECT p.* FROM posts p JOIN followers f ON f.following_id = p.user_id " +
            "WHERE f.follower_id = :userId " +
            "ORDER BY p.created_date DESC, p.post_id DESC LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Post> findFollowingFeed(@Param("userId") Long userId,
                                 @Param("limit") int limit,
                                 @Param("offset") int offset);

    /**
     * Продовження стрічки після рекомендацій: чужі пости від найновіших, крім уже показаних.
     * @param excludedIds не може бути порожнім (NOT IN ())
     */
    @Query(value = "SELECT * FROM posts WHERE user_id <> :userId AND post_id NOT IN (:excludedIds) " +
            "ORDER BY created_date DESC, post_id DESC LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Post> findChronologicalExcluding(@Param("userId") Long userId,
                                          @Param("excludedIds") Collection<Long> excludedIds,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset);

    @Query(value = "SELECT * FROM posts WHERE embedding IS NULL", nativeQuery = true)
    List<Post> findByEmbeddingIsNull();

    @Query("SELECT DISTINCT p FROM Post p WHERE " +
            "LOWER(p.text) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(p.label) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "ORDER BY p.createdDate DESC")
    List<Post> searchPostsByText(@Param("q") String query, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Post p JOIN p.tags t WHERE " +
            "LOWER(t.name) = LOWER(:tag) " +
            "ORDER BY p.createdDate DESC")
    List<Post> searchPostsByTag(@Param("tag") String tag, Pageable pageable);
}