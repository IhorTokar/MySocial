package com.example.social.client.controller;

import com.example.social.client.service.AdminApiService;
import com.example.social.client.service.ApiClient;
import com.example.social.client.service.PostApiService.PostItem;
import com.example.social.client.util.AdminFormat;
import com.example.social.client.util.Async;
import com.example.social.shared.dto.AdminCommentDto;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;

/** Вкладка «Контент»: пошук поста або коментаря за ID і видалення з причиною. */
public class AdminContentController {

    @FXML private TextField postIdField;
    @FXML private VBox postBox;
    @FXML private Label postMetaLabel;
    @FXML private Label postTextLabel;
    @FXML private Label postTagsLabel;
    @FXML private TextField postReasonField;
    @FXML private Button deletePostButton;
    @FXML private Label postStatusLabel;

    @FXML private TextField commentIdField;
    @FXML private VBox commentBox;
    @FXML private Label commentMetaLabel;
    @FXML private Label commentTextLabel;
    @FXML private TextField commentReasonField;
    @FXML private Button deleteCommentButton;
    @FXML private Label commentStatusLabel;

    private final AdminApiService api = new AdminApiService(new ApiClient());
    private PostItem currentPost;
    private AdminCommentDto currentComment;
    private LongConsumer onOpenUser;

    /** Що робити з кнопкою «Керувати автором» — відкрити користувача на вкладці «Користувачі». */
    public void setOnOpenUser(LongConsumer onOpenUser) {
        this.onOpenUser = onOpenUser;
    }

    // ===== Пост =====

    @FXML
    private void handleFindPost() {
        Long postId = parseId(postIdField, postStatusLabel);
        if (postId == null) {
            return;
        }
        showPost(null);
        showStatus(postStatusLabel, "Пошук...", false);
        Async.run(() -> {
            PostItem post = api.getPost(postId);
            Platform.runLater(() -> {
                showPost(post);
                showStatus(postStatusLabel, "", false);
            });
        }, error -> showStatus(postStatusLabel, error, true), null);
    }

    private void showPost(PostItem post) {
        currentPost = post;
        postBox.setVisible(post != null);
        postBox.setManaged(post != null);
        if (post == null) {
            return;
        }
        postMetaLabel.setText("Пост #" + post.postId() + " · автор @" + post.authorUsername()
                + " (ID " + post.authorId() + ") · " + formatIso(post.createdDate())
                + "\n♥ " + post.likesCount() + " · коментарів: " + post.commentsCount()
                + (post.mediaUrl() != null ? " · є зображення" : ""));

        String label = post.label() == null || post.label().isBlank() ? "" : post.label() + "\n";
        postTextLabel.setText(label + (post.text() == null ? "" : post.text()));

        String tags = post.tags().stream().map(t -> "#" + t).collect(Collectors.joining(" "));
        postTagsLabel.setText(tags);
        postTagsLabel.setVisible(!tags.isEmpty());
        postTagsLabel.setManaged(!tags.isEmpty());
        postReasonField.clear();
    }

    @FXML
    private void handleDeletePost() {
        PostItem post = currentPost;
        if (post == null) {
            return;
        }
        if (!confirm("Видалити пост #" + post.postId() + " автора @" + post.authorUsername() + "?\n"
                + "Разом з ним видаляться коментарі (" + post.commentsCount() + ") і лайки. Дію неможливо скасувати.")) {
            return;
        }
        String reason = postReasonField.getText();
        deletePostButton.setDisable(true);
        Async.run(() -> {
            api.deletePost(post.postId(), reason);
            Platform.runLater(() -> {
                showPost(null);
                showStatus(postStatusLabel, "Пост #" + post.postId() + " видалено, запис додано в журнал.", false);
            });
        }, error -> showStatus(postStatusLabel, error, true), () -> deletePostButton.setDisable(false));
    }

    @FXML
    private void handleOpenPostAuthor() {
        if (currentPost != null && onOpenUser != null) {
            onOpenUser.accept(currentPost.authorId());
        }
    }

    // ===== Коментар =====

    @FXML
    private void handleFindComment() {
        Long commentId = parseId(commentIdField, commentStatusLabel);
        if (commentId == null) {
            return;
        }
        showComment(null);
        showStatus(commentStatusLabel, "Пошук...", false);
        Async.run(() -> {
            AdminCommentDto comment = api.getComment(commentId);
            Platform.runLater(() -> {
                showComment(comment);
                showStatus(commentStatusLabel, "", false);
            });
        }, error -> showStatus(commentStatusLabel, error, true), null);
    }

    private void showComment(AdminCommentDto comment) {
        currentComment = comment;
        commentBox.setVisible(comment != null);
        commentBox.setManaged(comment != null);
        if (comment == null) {
            return;
        }
        commentMetaLabel.setText("Коментар #" + comment.commentId() + " · автор @" + comment.authorUsername()
                + " (ID " + comment.authorId() + ") · пост #" + comment.postId()
                + (comment.parentCommentId() != null ? " · відповідь на #" + comment.parentCommentId() : "")
                + " · " + AdminFormat.dateTime(comment.createdAt())
                + (comment.repliesCount() > 0
                ? "\nВідповідей: " + comment.repliesCount() + " — видаляться разом з коментарем"
                : ""));
        commentTextLabel.setText(comment.text());
        commentReasonField.clear();
    }

    @FXML
    private void handleDeleteComment() {
        AdminCommentDto comment = currentComment;
        if (comment == null) {
            return;
        }
        String replies = comment.repliesCount() > 0
                ? "\nРазом з ним видаляться відповіді (" + comment.repliesCount() + ")." : "";
        if (!confirm("Видалити коментар #" + comment.commentId() + " автора @" + comment.authorUsername() + "?"
                + replies + "\nДію неможливо скасувати.")) {
            return;
        }
        String reason = commentReasonField.getText();
        deleteCommentButton.setDisable(true);
        Async.run(() -> {
            api.deleteComment(comment.commentId(), reason);
            Platform.runLater(() -> {
                showComment(null);
                showStatus(commentStatusLabel,
                        "Коментар #" + comment.commentId() + " видалено, запис додано в журнал.", false);
            });
        }, error -> showStatus(commentStatusLabel, error, true), () -> deleteCommentButton.setDisable(false));
    }

    @FXML
    private void handleOpenCommentAuthor() {
        if (currentComment != null && onOpenUser != null) {
            onOpenUser.accept(currentComment.authorId());
        }
    }

    /** Підставляє ID поста, до якого належить коментар, і знаходить його. */
    @FXML
    private void handleShowCommentPost() {
        if (currentComment != null) {
            postIdField.setText(String.valueOf(currentComment.postId()));
            handleFindPost();
        }
    }

    // ===== Допоміжне =====

    /** ID з поля: приймає і «89», і «#89» (так він копіюється з мітки). */
    private Long parseId(TextField field, Label status) {
        String text = field.getText() == null ? "" : field.getText().trim().replace("#", "");
        try {
            long id = Long.parseLong(text);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            // нижче — повідомлення
        }
        showStatus(status, "ID має бути додатним числом", true);
        return null;
    }

    private static String formatIso(String iso) {
        try {
            return AdminFormat.dateTime(LocalDateTime.parse(iso));
        } catch (DateTimeParseException | NullPointerException e) {
            return AdminFormat.orDash(iso);
        }
    }

    private static void showStatus(Label label, String text, boolean error) {
        label.setText(text);
        label.getStyleClass().removeAll("secondary-label", "error-label", "success-label");
        if (!text.isEmpty()) {
            label.getStyleClass().add(error ? "error-label" : "success-label");
        }
    }

    private static boolean confirm(String text) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text);
        alert.setHeaderText(null);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}