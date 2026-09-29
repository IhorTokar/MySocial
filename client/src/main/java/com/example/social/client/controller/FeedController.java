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
    private Button refreshButton;

    private final PostApiService postApiService = new PostApiService(new ApiClient());
    private MainShellController shell;

    @FXML
    private void initialize() {
        postsListView.setCellFactory(list -> {
            PostListCell cell = new PostListCell(this::openProfile);
            cell.setOnEditRequested(this::openEditForm);
            return cell;
        });
        loadFeed();
    }

    private void openEditForm(PostApiService.PostItem post) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/create_post.fxml"));
            javafx.scene.Parent root = loader.load();

            CreatePostController controller = loader.getController();
            controller.setEditMode(post);
            controller.setOnPublished(this::loadFeed);

            javafx.stage.Stage modal = new javafx.stage.Stage();
            modal.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            modal.setTitle("Редагувати пост");
            modal.setScene(new javafx.scene.Scene(root, 500, 420));
            modal.showAndWait();
        } catch (java.io.IOException e) {
            statusLabel.setText("Помилка відкриття редагування: " + e.getMessage());
        }
    }

    private void openProfile(Long userId) {
        if (shell != null) {
            shell.showProfile(userId);
        }
    }

    public void setShell(com.example.social.client.controller.MainShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void handleRefresh() {
        loadFeed();
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


}