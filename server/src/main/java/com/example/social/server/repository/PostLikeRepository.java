package com.example.social.server.repository;

import com.example.social.server.entity.Post;
import com.example.social.server.entity.PostLike;
import com.example.social.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    Optional<PostLike> findByUserAndPost(User user, Post post);
    boolean existsByUserAndPost(User user, Post post);
    long countByPost(Post post);

    @Query("SELECT pl.post.postId AS postId, COUNT(pl) AS cnt " +
            "FROM PostLike pl WHERE pl.likedAt >= :since GROUP BY pl.post.postId")
    List<Object[]> countLikesSince(@Param("since") LocalDateTime since);

    List<PostLike> findByUserIn(List<User> users);
    List<PostLike> findByUser(User user);
    List<PostLike> findByPostIn(List<Post> posts);
}