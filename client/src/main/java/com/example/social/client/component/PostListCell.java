package com.example.social.client.component;

import com.example.social.client.controller.CommentsWindow;
import com.example.social.client.service.ApiClient;
import com.example.social.client.service.LikeApiService;
import com.example.social.client.service.PostApiService;
import com.example.social.client.service.SavePostApiService;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class PostListCell extends ListCell<PostApiService.PostItem> {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM, HH:mm", new Locale("uk"));

    private final LikeApiService likeApiService = new LikeApiService(new ApiClient());
    private final SavePostApiService saveApiService = new SavePostApiService(new ApiClient());

    @Override
    protected void updateItem(PostApiService.PostItem post, boolean empty) {
        super.updateItem(post, empty);

        if (empty || post == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        Label authorLabel = new Label(post.authorUsername());
        authorLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label textLabel = new Label(post.text());
        textLabel.setWrapText(true);

        Label dateLabel = new Label(formatDate(post.createdDate()));
        dateLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");

        Label statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");

        Button likeButton = new Button("❤ Лайк");
        likeButton.setDisable(true);

        Button saveButton = new Button("🔖 Зберегти");
        saveButton.setDisable(true);

        Button commentsButton = new Button("💬 Коментарі");
        commentsButton.setOnAction(e -> new CommentsWindow(post.postId()).show());

        HBox actionsBox = new HBox(8, likeButton, saveButton, commentsButton);
        VBox box = new VBox(4, authorLabel, textLabel, dateLabel, actionsBox, statusLabel);
        box.setStyle("-fx-padding: 8px;");

        setGraphic(box);
        setText(null);

        loadStatuses(post.postId(), likeButton, saveButton, statusLabel);
    }

    private void loadStatuses(Long postId, Button likeButton, Button saveButton, Label statusLabel) {
        Thread.ofVirtual().start(() -> {
            try {
                LikeApiService.StatusResult likeStatus = likeApiService.getStatus(postId);
                boolean saved = saveApiService.getStatus(postId);

                Platform.runLater(() -> {
                    likeButton.setDisable(false);
                    saveButton.setDisable(false);
                    updateLikeButtonText(likeButton, likeStatus.liked(), likeStatus.likesCount());
                    updateSaveButtonText(saveButton, saved);

                    likeButton.setOnAction(e -> handleLike(postId, likeButton, statusLabel));
                    saveButton.setOnAction(e -> handleSave(postId, saveButton, statusLabel));
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Не вдалося завантажити статус"));
            }
        });
    }

    private void handleLike(Long postId, Button button, Label statusLabel) {
        boolean currentlyLiked = button.getText().startsWith("💔");
        button.setDisable(true);

        Thread.ofVirtual().start(() -> {
            try {
                LikeApiService.ActionResult result = currentlyLiked
                        ? likeApiService.unlike(postId)
                        : likeApiService.like(postId);

                LikeApiService.StatusResult newStatus = likeApiService.getStatus(postId);

                Platform.runLater(() -> {
                    button.setDisable(false);
                    if (result.success()) {
                        updateLikeButtonText(button, newStatus.liked(), newStatus.likesCount());
                        statusLabel.setText("");
                    } else {
                        statusLabel.setText(result.errorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    button.setDisable(false);
                    statusLabel.setText("Помилка з'єднання");
                });
            }
        });
    }

    private void handleSave(Long postId, Button button, Label statusLabel) {
        boolean currentlySaved = button.getText().startsWith("✅");
        button.setDisable(true);

        Thread.ofVirtual().start(() -> {
            try {
                SavePostApiService.ActionResult result = currentlySaved
                        ? saveApiService.unsave(postId)
                        : saveApiService.save(postId);

                Platform.runLater(() -> {
                    button.setDisable(false);
                    if (result.success()) {
                        updateSaveButtonText(button, !currentlySaved);
                        statusLabel.setText("");
                    } else {
                        statusLabel.setText(result.errorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    button.setDisable(false);
                    statusLabel.setText("Помилка з'єднання");
                });
            }
        });
    }

    private void updateLikeButtonText(Button button, boolean liked, long count) {
        button.setText((liked ? "💔 Прибрати лайк" : "❤ Лайк") + " (" + count + ")");
    }

    private void updateSaveButtonText(Button button, boolean saved) {
        button.setText(saved ? "✅ Збережено" : "🔖 Зберегти");
    }

    private String formatDate(String isoDate) {
        try {
            LocalDateTime dateTime = LocalDateTime.parse(isoDate);
            return dateTime.format(DISPLAY_FORMAT);
        } catch (Exception e) {
            return isoDate;
        }
    }
}