package com.example.social.server.repository;

import com.example.social.server.entity.Post;
import com.example.social.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByUserOrderByCreatedDateDesc(User user);
    List<Post> findByUserInOrderByCreatedDateDesc(List<User> users);
}