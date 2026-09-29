package com.example.social.server.repository;

import com.example.social.server.entity.Comment;
import com.example.social.server.entity.Post;
import com.example.social.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostOrderByCreatedAtAsc(Post post);
    List<Comment> findByParentComment(Comment parentComment);
    List<Comment> findByUser(User user);
    List<Comment> findByPostIn(List<Post> posts);
    List<Comment> findByUserIn(List<User> users);

    @Query("SELECT c.post.postId, COUNT(c) FROM Comment c " +
            "WHERE c.post.postId IN :postIds GROUP BY c.post.postId")
    List<Object[]> countByPostIds(@Param("postIds") Collection<Long> postIds);
}