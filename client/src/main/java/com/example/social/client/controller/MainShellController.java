package com.example.social.client.controller;

import com.example.social.client.util.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

public class MainShellController {

    @FXML
    private StackPane contentArea;

    @FXML
    private void initialize() {
        showFeed();
    }

    public void showFeed() {
        FeedController controller = loadIntoContent("/fxml/feed.fxml");
        if (controller != null) {
            controller.setShell(this);
        }
    }

    public void showProfile(Long userId) {
        ProfileController controller = loadIntoContent("/fxml/profile.fxml");
        if (controller != null) {
            controller.setShell(this);
            controller.setUserId(userId);
        }
    }

    public void showMyProfile() {
        showProfile(SessionManager.getInstance().getUserId());
    }

    public void showSaved() {
        SavedController controller = loadIntoContent("/fxml/saved.fxml");
        if (controller != null) {
            controller.setShell(this);
        }
    }

    @FXML
    private void handleShowFeed() {
        showFeed();
    }

    @FXML
    private void handleShowMyProfile() {
        showMyProfile();
    }

    @FXML
    private void handleShowSaved() {
        showSaved();
    }

    @FXML
    private void handleNewPost() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/create_post.fxml"));
            Parent root = loader.load();

            CreatePostController controller = loader.getController();
            controller.setOnPublished(this::showFeed);

            Stage modal = new Stage();
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setTitle("Новий пост");
            modal.setScene(new javafx.scene.Scene(root, 500, 420));
            modal.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleLogout() {
        SessionManager.getInstance().clear();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) contentArea.getScene().getWindow();
            stage.setScene(new javafx.scene.Scene(root, 400, 400));
            stage.setTitle("Соціальна мережа");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T loadIntoContent(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent node = loader.load();
            contentArea.getChildren().setAll(node);
            return loader.getController();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}