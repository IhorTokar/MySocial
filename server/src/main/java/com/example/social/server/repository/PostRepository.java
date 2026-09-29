package com.example.social.server.repository;

import com.example.social.server.entity.Post;
import com.example.social.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByUserOrderByCreatedDateDesc(User user);
    List<Post> findByUserInOrderByCreatedDateDesc(List<User> users);
    List<Post> findTop50ByOrderByCreatedDateDesc();
    long countByUser(User user);

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