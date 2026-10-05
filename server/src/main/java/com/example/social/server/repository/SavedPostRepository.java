package com.example.social.server.repository;

import com.example.social.server.entity.Post;
import com.example.social.server.entity.SavedPost;
import com.example.social.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SavedPostRepository extends JpaRepository<SavedPost, Long> {
    Optional<SavedPost> findByUserAndPost(User user, Post post);
    List<SavedPost> findByUser(User user);
    boolean existsByUserAndPost(User user, Post post);
    List<SavedPost> findByPost(Post post);

    @Query("SELECT sp.post.postId FROM SavedPost sp " +
            "WHERE sp.user.userId = :userId AND sp.post.postId IN :postIds")
    List<Long> findSavedPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);
}