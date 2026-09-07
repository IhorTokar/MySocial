package com.example.social.server.repository;

import com.example.social.server.entity.Post;
import com.example.social.server.entity.PostLike;
import com.example.social.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    Optional<PostLike> findByUserAndPost(User user, Post post);
    boolean existsByUserAndPost(User user, Post post);
    long countByPost(Post post);
}