package com.example.social.server.repository;

import com.example.social.server.entity.Post;
import com.example.social.server.entity.SavedPost;
import com.example.social.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SavedPostRepository extends JpaRepository<SavedPost, Long> {
    Optional<SavedPost> findByUserAndPost(User user, Post post);
    List<SavedPost> findByUser(User user);
    boolean existsByUserAndPost(User user, Post post);
}