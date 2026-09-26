package com.example.social.server.repository;

import com.example.social.server.entity.Comment;
import com.example.social.server.entity.Post;
import com.example.social.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostOrderByCreatedAtAsc(Post post);
    List<Comment> findByParentComment(Comment parentComment);
    List<Comment> findByUser(User user);
    List<Comment> findByPostIn(List<Post> posts);
    List<Comment> findByUserIn(List<User> users);
}