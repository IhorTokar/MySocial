package com.example.social.client.controller;

import com.example.social.client.service.ApiClient;
import com.example.social.client.service.CommentApiService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class CommentsWindow {

    private final CommentApiService commentApiService = new CommentApiService(new ApiClient());
    private final Long postId;

    private ListView<CommentApiService.CommentItem> listView;
    private Label statusLabel;

    public CommentsWindow(Long postId) {
        this.postId = postId;
    }

    public void show() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Коментарі");

        listView = new ListView<>();
        listView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(CommentApiService.CommentItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.authorUsername() + ": " + item.text());
                }
            }
        });

        TextField inputField = new TextField();
        inputField.setPromptText("Написати коментар...");
        Button sendButton = new Button("Надіслати");
        statusLabel = new Label();

        sendButton.setOnAction(e -> {
            String text = inputField.getText().trim();
            if (text.isEmpty()) {
                return;
            }
            sendButton.setDisable(true);

            Thread.ofVirtual().start(() -> {
                try {
                    CommentApiService.ActionResult result = commentApiService.addComment(postId, text);
                    Platform.runLater(() -> {
                        sendButton.setDisable(false);
                        if (result.success()) {
                            inputField.clear();
                            loadComments();
                        } else {
                            statusLabel.setText(result.errorMessage());
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        sendButton.setDisable(false);
                        statusLabel.setText("Помилка з'єднання: " + ex.getMessage());
                    });
                }
            });
        });

        HBox inputBox = new HBox(8, inputField, sendButton);
        inputBox.setPadding(new Insets(8));

        BorderPane root = new BorderPane();
        root.setCenter(listView);
        root.setBottom(new javafx.scene.layout.VBox(statusLabel, inputBox));

        stage.setScene(new Scene(root, 400, 400));
        stage.show();

        loadComments();
    }

    private void loadComments() {
        Thread.ofVirtual().start(() -> {
            try {
                CommentApiService.CommentsResult result = commentApiService.getComments(postId);
                Platform.runLater(() -> {
                    if (result.success()) {
                        listView.getItems().setAll(result.comments());
                    } else {
                        statusLabel.setText(result.errorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Помилка завантаження: " + e.getMessage()));
            }
        });
    }
}