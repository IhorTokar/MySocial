package com.example.social.client.controller;

import com.example.social.client.component.PostListCell;
import com.example.social.client.service.ApiClient;
import com.example.social.client.service.FollowApiService;
import com.example.social.client.service.PostApiService;
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

public class ProfileController {

    @FXML
    private Label usernameLabel;

    @FXML
    private Label followersCountLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private ListView<PostApiService.PostItem> postsListView;

    @FXML
    private Button backButton;

    private final PostApiService postApiService = new PostApiService(new ApiClient());
    private final FollowApiService followApiService = new FollowApiService(new ApiClient());

    private Long profileUserId;

    public void setUserId(Long userId) {
        this.profileUserId = userId;
        usernameLabel.setText("Користувач #" + userId);
        postsListView.setCellFactory(list -> new PostListCell());
        loadProfile();
    }

    private void loadProfile() {
        Thread.ofVirtual().start(() -> {
            try {
                long followersCount = followApiService.getFollowersCount(profileUserId);
                PostApiService.FeedResult postsResult = postApiService.getPostsByUser(profileUserId);

                Platform.runLater(() -> {
                    followersCountLabel.setText(followersCount + " підписників");

                    if (postsResult.success()) {
                        postsListView.getItems().setAll(postsResult.posts());
                    } else {
                        statusLabel.setText(postsResult.errorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Помилка завантаження: " + e.getMessage()));
            }
        });
    }

    @FXML
    private void handleFollow() {
        performFollowAction(true);
    }

    @FXML
    private void handleUnfollow() {
        performFollowAction(false);
    }

    private void performFollowAction(boolean follow) {
        statusLabel.setStyle("-fx-text-fill: black;");
        statusLabel.setText("Обробка...");

        Thread.ofVirtual().start(() -> {
            try {
                FollowApiService.ActionResult result = follow
                        ? followApiService.follow(profileUserId)
                        : followApiService.unfollow(profileUserId);

                Platform.runLater(() -> {
                    if (result.success()) {
                        statusLabel.setStyle("-fx-text-fill: green;");
                        statusLabel.setText(result.message());
                        loadProfile();
                    } else {
                        statusLabel.setStyle("-fx-text-fill: red;");
                        statusLabel.setText(result.message());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setStyle("-fx-text-fill: red;");
                    statusLabel.setText("Помилка з'єднання: " + e.getMessage());
                });
            }
        });
    }

    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/feed.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) backButton.getScene().getWindow();
            stage.setScene(new Scene(root, 900, 600));
            stage.setTitle("Стрічка");
        } catch (IOException e) {
            statusLabel.setText("Помилка переходу: " + e.getMessage());
        }
    }
}