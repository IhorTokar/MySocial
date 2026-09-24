package com.example.social.client.component;

import com.example.social.client.controller.CommentsWindow;
import com.example.social.client.service.ApiClient;
import com.example.social.client.service.LikeApiService;
import com.example.social.client.service.PostApiService;
import com.example.social.client.service.SavePostApiService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;


public class PostListCell extends ListCell<PostApiService.PostItem> {

    private static final String BASE_URL = "http://localhost:8080";
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
        authorLabel.getStyleClass().add("post-author-name");

        Label textLabel = new Label(post.text());
        textLabel.getStyleClass().add("post-text");
        textLabel.setWrapText(true);

        Label dateLabel = new Label(formatDate(post.createdDate()));
        dateLabel.getStyleClass().add("post-date");

        VBox box = new VBox(6, authorLabel, textLabel);

        if (post.mediaUrl() != null && !post.mediaUrl().isBlank()) {
            ImageView imageView = createPostImage(post.mediaUrl());
            if (imageView != null) {
                box.getChildren().add(imageView);
            }
        }

        box.getChildren().add(dateLabel);

        Label statusLabel = new Label();
        statusLabel.getStyleClass().add("secondary-label");

        Button likeButton = new Button("♡ Лайк");
        likeButton.getStyleClass().add("action-button");
        likeButton.setDisable(true);

        Button saveButton = new Button("🔖 Зберегти");
        saveButton.getStyleClass().add("action-button");
        saveButton.setDisable(true);

        Button commentsButton = new Button("💬 Коментарі");
        commentsButton.getStyleClass().add("action-button");
        commentsButton.setOnAction(e -> new CommentsWindow(post.postId()).show());

        HBox actionsBox = new HBox(4, likeButton, saveButton, commentsButton);
        actionsBox.setPadding(new Insets(8, 0, 0, 0));

        box.getChildren().addAll(actionsBox, statusLabel);
        box.getStyleClass().add("post-card");
        VBox.setMargin(box, new Insets(4, 8, 4, 8));

        setGraphic(box);
        setText(null);

        loadStatuses(post.postId(), likeButton, saveButton, statusLabel);
    }

    private ImageView createPostImage(String mediaUrl) {
        try {
            String fullUrl = mediaUrl.startsWith("http") ? mediaUrl : BASE_URL + mediaUrl;
            Image image = new Image(fullUrl, 400, 0, true, true, true);

            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(400);
            imageView.setPreserveRatio(true);
            imageView.getStyleClass().add("post-image");
            return imageView;
        } catch (Exception e) {
            return null;
        }
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
        boolean currentlyLiked = button.getStyleClass().contains("like-active");
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
        boolean currentlySaved = button.getStyleClass().contains("save-active");
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
        button.setText((liked ? "♥ Вподобано" : "♡ Лайк") + " (" + count + ")");
        button.getStyleClass().remove("like-active");
        if (liked) {
            button.getStyleClass().add("like-active");
        }
    }

    private void updateSaveButtonText(Button button, boolean saved) {
        button.setText(saved ? "🔖 Збережено" : "🔖 Зберегти");
        button.getStyleClass().remove("save-active");
        if (saved) {
            button.getStyleClass().add("save-active");
        }
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