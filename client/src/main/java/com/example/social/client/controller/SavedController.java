package com.example.social.client.controller;

import com.example.social.client.component.PostListCell;
import com.example.social.client.service.ApiClient;
import com.example.social.client.service.PostApiService;
import com.example.social.client.service.SavePostApiService;
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
        postsListView.setCellFactory(list -> new PostListCell());
        loadSaved();
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
}