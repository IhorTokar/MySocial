package com.example.social.server.service;

import com.example.social.server.entity.AuditAction;
import com.example.social.server.entity.Comment;
import com.example.social.server.entity.Post;
import com.example.social.server.exception.NotFoundException;
import com.example.social.server.repository.CommentRepository;
import com.example.social.server.repository.PostRepository;
import com.example.social.shared.dto.AdminCommentDto;
import com.example.social.shared.dto.PostResponseDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Модерація контенту за ID. Перед видаленням у журнал записується автор і початок тексту,
 * щоб після видалення було видно, що саме прибрали.
 */
@Service
public class AdminContentService {

    private static final int SNIPPET_LENGTH = 80;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostService postService;
    private final AuditService auditService;

    public AdminContentService(PostRepository postRepository,
                               CommentRepository commentRepository,
                               PostService postService,
                               AuditService auditService) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.postService = postService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PostResponseDto getPost(long postId, Long adminId) {
        return postService.getPost(postId, adminId);
    }

    /** Видаляє пост; лайки, коментарі, теги й збереження видаляє база (ON DELETE CASCADE). */
    @Transactional
    public void deletePost(long postId, String reason, Long adminId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Post not found: " + postId));

        String details = "автор: " + post.getUser().getUsername() + " (id " + post.getUser().getUserId() + ")"
                + reasonPart(reason)
                + "; текст: «" + snippet(post.getText() != null ? post.getText() : post.getLabel()) + "»";

        postRepository.delete(post);
        auditService.log(adminId, AuditAction.POST_DELETE, "POST", postId, details);
    }

    @Transactional(readOnly = true)
    public AdminCommentDto getComment(long commentId) {
        return toDto(commentOf(commentId));
    }

    /** Видаляє коментар разом з усіма відповідями на нього (ON DELETE CASCADE). */
    @Transactional
    public void deleteComment(long commentId, String reason, Long adminId) {
        Comment comment = commentOf(commentId);
        long replies = commentRepository.countByParentComment(comment);

        String details = "пост " + comment.getPost().getPostId()
                + "; автор: " + comment.getUser().getUsername() + " (id " + comment.getUser().getUserId() + ")"
                + (replies > 0 ? "; відповідей видалено: " + replies : "")
                + reasonPart(reason)
                + "; текст: «" + snippet(comment.getText()) + "»";

        commentRepository.delete(comment);
        auditService.log(adminId, AuditAction.COMMENT_DELETE, "COMMENT", commentId, details);
    }

    private Comment commentOf(long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found: " + commentId));
    }

    private AdminCommentDto toDto(Comment c) {
        return new AdminCommentDto(
                c.getCommentId(),
                c.getPost().getPostId(),
                c.getUser().getUserId(),
                c.getUser().getUsername(),
                c.getText(),
                c.getCreatedAt(),
                c.getParentComment() == null ? null : c.getParentComment().getCommentId(),
                commentRepository.countByParentComment(c));
    }

    private static String reasonPart(String reason) {
        return reason == null || reason.isBlank() ? "" : "; причина: " + reason.trim();
    }

    private static String snippet(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= SNIPPET_LENGTH ? oneLine : oneLine.substring(0, SNIPPET_LENGTH) + "…";
    }
}