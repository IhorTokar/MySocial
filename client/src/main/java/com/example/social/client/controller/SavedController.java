package com.example.social.client.controller;

import com.example.social.client.component.PostListCell;
import com.example.social.client.service.ApiClient;
import com.example.social.client.service.PostApiService;
import com.example.social.client.service.SavePostApiService;
import com.example.social.client.util.SceneUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

public class SavedController {

    @FXML
    private ListView<PostApiService.PostItem> postsListView;

    @FXML
    private Label statusLabel;

    @FXML
    private Button refreshButton;

    private final SavePostApiService savePostApiService = new SavePostApiService(new ApiClient());
    private MainShellController shell;

    public void setShell(MainShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void initialize() {
        postsListView.setCellFactory(list -> {
            PostListCell cell = new PostListCell(this::openProfile);
            cell.setOnEditRequested(this::openEditForm);
            return cell;
        });
        loadSaved();
    }

    private void openEditForm(PostApiService.PostItem post) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/create_post.fxml"));
            javafx.scene.Parent root = loader.load();

            CreatePostController controller = loader.getController();
            controller.setEditMode(post);
            controller.setOnPublished(this::loadSaved);

            javafx.stage.Stage modal = new javafx.stage.Stage();
            modal.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            modal.setTitle("Редагувати пост");
            modal.setScene(SceneUtil.create(root, 500, 420));
            modal.showAndWait();
        } catch (java.io.IOException e) {
            statusLabel.setText("Помилка відкриття редагування: " + e.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        loadSaved();
    }

    private void loadSaved() {
        refreshButton.setDisable(true);
        statusLabel.setText("Завантаження...");

        Thread.ofVirtual().start(() -> {
            try {
                SavePostApiService.FetchResult result = savePostApiService.getSavedPosts();

                Platform.runLater(() -> {
                    refreshButton.setDisable(false);

                    if (result.success()) {
                        postsListView.getItems().setAll(result.posts());
                        statusLabel.setText("Збережених постів: " + result.posts().size());
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

    private void openProfile(Long userId) {
        if (shell != null) {
            shell.showProfile(userId);
        }
    }
}