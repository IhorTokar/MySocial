package com.example.social.client.controller;

import com.example.social.client.component.PostListCell;
import com.example.social.client.component.UserSearchCell;
import com.example.social.client.service.ApiClient;
import com.example.social.client.service.PostApiService;
import com.example.social.client.service.SearchApiService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

public class SearchController {

    @FXML private Label queryLabel;
    @FXML private Label statusLabel;
    @FXML private Label usersHeaderLabel;
    @FXML private ListView<SearchApiService.UserSummary> usersListView;
    @FXML private Label postsHeaderLabel;
    @FXML private ListView<PostApiService.PostItem> postsListView;

    private final SearchApiService searchApiService = new SearchApiService(new ApiClient());
    private MainShellController shell;

    public void setShell(MainShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void initialize() {
        usersListView.setCellFactory(list -> new UserSearchCell(this::openProfile));
        postsListView.setCellFactory(list -> {
            PostListCell cell = new PostListCell(this::openProfile);
            cell.setOnEditRequested(this::openEditForm);
            return cell;
        });
    }

    public void runSearch(String query) {
        queryLabel.setText("Результати пошуку: \"" + query + "\"");
        statusLabel.setText("Пошук...");
        usersListView.getItems().clear();
        postsListView.getItems().clear();

        Thread.ofVirtual().start(() -> {
            try {
                SearchApiService.SearchResult result = searchApiService.search(query);

                Platform.runLater(() -> {
                    if (result.success()) {
                        usersListView.getItems().setAll(result.users());
                        postsListView.getItems().setAll(result.posts());
                        usersHeaderLabel.setText(query.startsWith("#")
                                ? "Користувачі (пошук за тегом не застосовується)"
                                : "Користувачі (" + result.users().size() + ")");
                        postsHeaderLabel.setText("Пости (" + result.posts().size() + ")");
                        statusLabel.setText(result.users().isEmpty() && result.posts().isEmpty()
                                ? "Нічого не знайдено" : "");
                    } else {
                        statusLabel.getStyleClass().setAll("error-label");
                        statusLabel.setText(result.errorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.getStyleClass().setAll("error-label");
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

    private void openEditForm(PostApiService.PostItem post) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/create_post.fxml"));
            javafx.scene.Parent root = loader.load();

            CreatePostController controller = loader.getController();
            controller.setEditMode(post);

            javafx.stage.Stage modal = new javafx.stage.Stage();
            modal.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            modal.setTitle("Редагувати пост");
            modal.setScene(new javafx.scene.Scene(root, 500, 420));
            modal.showAndWait();
        } catch (java.io.IOException e) {
            statusLabel.setText("Помилка відкриття редагування: " + e.getMessage());
        }
    }
}