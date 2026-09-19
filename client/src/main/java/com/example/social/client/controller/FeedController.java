package com.example.social.client.controller;

import com.example.social.client.component.PostListCell;
import com.example.social.client.service.ApiClient;
import com.example.social.client.service.PostApiService;
import com.example.social.client.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

import java.io.IOException;

public class FeedController {

    @FXML
    private ListView<PostApiService.PostItem> postsListView;

    @FXML
    private Label statusLabel;

    @FXML
    private Button newPostButton;

    @FXML
    private javafx.scene.control.TextField profileUserIdField;

    @FXML
    private Button refreshButton;

    private final PostApiService postApiService = new PostApiService(new ApiClient());

    @FXML
    private void initialize() {
        postsListView.setCellFactory(list -> new PostListCell());
        loadFeed();
    }

    @FXML
    private void handleRefresh() {
        loadFeed();
    }

    @FXML
    private void handleNewPost() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/create_post.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) refreshButton.getScene().getWindow();
            stage.setScene(new Scene(root, 500, 450));
            stage.setTitle("Новий пост");
        } catch (IOException e) {
            statusLabel.setText("Помилка переходу: " + e.getMessage());
        }
    }

    @FXML
    private void handleViewProfile() {
        String idText = profileUserIdField.getText().trim();
        if (idText.isEmpty()) {
            statusLabel.setText("Введіть ID користувача");
            return;
        }

        try {
            Long userId = Long.parseLong(idText);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/profile.fxml"));
            Parent root = loader.load();

            ProfileController controller = loader.getController();
            controller.setUserId(userId);

            Stage stage = (Stage) refreshButton.getScene().getWindow();
            stage.setScene(new Scene(root, 900, 600));
            stage.setTitle("Профіль");
        } catch (NumberFormatException e) {
            statusLabel.setText("ID має бути числом");
        } catch (IOException e) {
            statusLabel.setText("Помилка переходу: " + e.getMessage());
        }
    }

    private void loadFeed() {
        refreshButton.setDisable(true);
        statusLabel.setText("Завантаження...");

        Thread.ofVirtual().start(() -> {
            try {
                PostApiService.FeedResult result = postApiService.getFeed();

                Platform.runLater(() -> {
                    refreshButton.setDisable(false);

                    if (result.success()) {
                        postsListView.getItems().setAll(result.posts());
                        statusLabel.setText("Постів у стрічці: " + result.posts().size());
                    } else {
                        statusLabel.setText(result.errorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    refreshButton.setDisable(false);
                    statusLabel.setText("Помилка з'єднання: " + e.getMessage());
                });
            }
        });
    }

    @FXML
    private void handleLogout() {
        SessionManager.getInstance().clear();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) refreshButton.getScene().getWindow();
            stage.setScene(new Scene(root, 400, 400));
            stage.setTitle("Соціальна мережа");
        } catch (IOException e) {
            statusLabel.setText("Помилка виходу: " + e.getMessage());
        }
    }
}