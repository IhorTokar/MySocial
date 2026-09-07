package com.example.social.server.repository;

import com.example.social.server.entity.Comment;
import com.example.social.server.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostOrderByCreatedAtAsc(Post post);
    List<Comment> findByParentComment(Comment parentComment);
}