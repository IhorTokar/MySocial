package com.example.social.client.controller;

import com.example.social.client.service.ApiClient;
import com.example.social.client.service.PostApiService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class CreatePostController {

    @FXML
    private Label titleLabel;

    @FXML
    private TextField labelField;

    @FXML
    private TextArea textArea;

    @FXML
    private TextField tagsField;

    @FXML
    private Label statusLabel;

    @FXML
    private Button submitButton;

    private final PostApiService postApiService = new PostApiService(new ApiClient());
    private Runnable onPublished;

    private Long editingPostId; // null = створення нового поста, інакше — редагування

    public void setOnPublished(Runnable onPublished) {
        this.onPublished = onPublished;
    }

    /** Перемикає форму в режим редагування наявного поста, заповнюючи поля його даними. */
    public void setEditMode(PostApiService.PostItem post) {
        this.editingPostId = post.postId();
        labelField.setText(post.label() != null ? post.label() : "");
        textArea.setText(post.text());
        tagsField.setText(post.tags().stream()
                .map(t -> "#" + t)
                .reduce((a, b) -> a + " " + b)
                .orElse(""));

        if (titleLabel != null) {
            titleLabel.setText("Редагувати пост");
        }
        submitButton.setText("Зберегти зміни");
    }

    @FXML
    private void handleSubmit() {
        String text = textArea.getText().trim();

        if (text.isEmpty()) {
            statusLabel.getStyleClass().setAll("error-label");
            statusLabel.setText("Текст поста не може бути порожнім");
            return;
        }

        String label = labelField.getText().trim();

        List<String> tags;
        try {
            tags = parseTags(tagsField.getText());
        } catch (IllegalArgumentException e) {
            statusLabel.getStyleClass().setAll("error-label");
            statusLabel.setText(e.getMessage());
            return;
        }

        submitButton.setDisable(true);
        statusLabel.getStyleClass().setAll("secondary-label");
        statusLabel.setText(editingPostId != null ? "Збереження..." : "Публікація...");

        Thread.ofVirtual().start(() -> {
            try {
                PostApiService.CreatePostResult result = editingPostId != null
                        ? postApiService.updatePost(editingPostId, label, text, tags)
                        : postApiService.createPost(label, text, tags);

                Platform.runLater(() -> {
                    submitButton.setDisable(false);
                    if (result.success()) {
                        if (onPublished != null) {
                            onPublished.run();
                        }
                        ((Stage) submitButton.getScene().getWindow()).close();
                    } else {
                        statusLabel.getStyleClass().setAll("error-label");
                        statusLabel.setText(result.errorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    submitButton.setDisable(false);
                    statusLabel.getStyleClass().setAll("error-label");
                    statusLabel.setText("Помилка з'єднання: " + e.getMessage());
                });
            }
        });
    }

    private List<String> parseTags(String rawTags) throws IllegalArgumentException {
        List<String> tags = new ArrayList<>();
        if (rawTags == null || rawTags.isBlank()) {
            return tags;
        }

        for (String token : rawTags.trim().split("\\s+")) {
            if (!token.startsWith("#")) {
                throw new IllegalArgumentException(
                        "Кожен тег має починатись з # (наприклад, #солодощі): \"" + token + "\"");
            }

            String tag = token.substring(1).trim();

            if (tag.isEmpty()) {
                throw new IllegalArgumentException("Тег не може складатись лише з символу #");
            }

            tags.add(tag);
        }

        return tags;
    }

    @FXML
    private void handleCancel() {
        ((Stage) submitButton.getScene().getWindow()).close();
    }
}