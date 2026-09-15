package com.example.social.server.service;

import com.example.social.server.entity.Comment;
import com.example.social.server.entity.Post;
import com.example.social.server.entity.User;
import com.example.social.server.repository.CommentRepository;
import com.example.social.server.repository.PostRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.shared.dto.CommentCreateDto;
import com.example.social.shared.dto.CommentResponseDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public CommentService(CommentRepository commentRepository,
                          PostRepository postRepository,
                          UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CommentResponseDto addComment(Long postId, Long userId, CommentCreateDto dto) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Comment comment = new Comment();
        comment.setPost(post);
        comment.setUser(user);
        comment.setText(dto.getText());

        if (dto.getParentCommentId() != null) {
            Comment parent = commentRepository.findById(dto.getParentCommentId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Parent comment not found: " + dto.getParentCommentId()));
            comment.setParentComment(parent);
        }

        Comment saved = commentRepository.save(comment);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<CommentResponseDto> getComments(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

        return commentRepository.findByPostOrderByCreatedAtAsc(post).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found: " + commentId));

        if (!comment.getUser().getUserId().equals(userId)) {
            throw new SecurityException("You can only delete your own comments");
        }

        commentRepository.delete(comment);
    }

    private CommentResponseDto toDto(Comment comment) {
        Long parentId = comment.getParentComment() != null
                ? comment.getParentComment().getCommentId()
                : null;

        return new CommentResponseDto(
                comment.getCommentId(),
                comment.getUser().getUsername(),
                comment.getText(),
                comment.getCreatedAt(),
                parentId
        );
    }
}