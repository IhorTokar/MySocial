package com.example.social.client.controller;

import com.example.social.client.service.ApiClient;
import com.example.social.client.service.PostApiService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CreatePostController {

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

    public void setOnPublished(Runnable onPublished) {
        this.onPublished = onPublished;
    }

    @FXML
    private void handleSubmit() {
        String text = textArea.getText().trim();

        if (text.isEmpty()) {
            statusLabel.setStyle("-fx-text-fill: red;");
            statusLabel.setText("Текст поста не може бути порожнім");
            return;
        }

        String label = labelField.getText().trim();

        List<String> tags;
        try {
            tags = parseTags(tagsField.getText());
        } catch (IllegalArgumentException e) {
            statusLabel.setStyle("-fx-text-fill: red;");
            statusLabel.setText(e.getMessage());
            return;
        }

        submitButton.setDisable(true);
        statusLabel.setStyle("-fx-text-fill: black;");
        statusLabel.setText("Публікація...");

        Thread.ofVirtual().start(() -> {
            try {
                PostApiService.CreatePostResult result = postApiService.createPost(label, text, tags);

                Platform.runLater(() -> {
                    submitButton.setDisable(false);
                    if (result.success()) {
                        if (onPublished != null) {
                            onPublished.run();
                        }
                        ((Stage) submitButton.getScene().getWindow()).close();
                    } else {
                        statusLabel.setStyle("-fx-text-fill: red;");
                        statusLabel.setText(result.errorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    submitButton.setDisable(false);
                    statusLabel.setStyle("-fx-text-fill: red;");
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

    private void goToFeed() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/feed.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) submitButton.getScene().getWindow();
            stage.setScene(new Scene(root, 900, 600));
            stage.setTitle("Стрічка");
        } catch (IOException e) {
            statusLabel.setText("Помилка переходу: " + e.getMessage());
        }
    }
}