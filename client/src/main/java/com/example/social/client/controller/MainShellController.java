package com.example.social.client.controller;

import com.example.social.client.util.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import com.example.social.client.service.ChatConnection;
import com.example.social.client.service.MessageApiService;
import com.example.social.client.service.UnreadCounter;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Label;

import java.io.IOException;

public class MainShellController {

    @FXML
    private StackPane contentArea;
    @FXML
    private Label unreadBadge;
    @FXML
    private javafx.scene.control.TextField searchField;

    private ChatConnection.Listener badgeListener;

    @FXML
    private void initialize() {
        ChatConnection chat = ChatConnection.getInstance();
        chat.connect();

        UnreadCounter counter = UnreadCounter.getInstance();

        unreadBadge.visibleProperty().bind(counter.countProperty().greaterThan(0));
        unreadBadge.textProperty().bind(Bindings.createStringBinding(
                () -> counter.countProperty().get() > 99 ? "99+" : String.valueOf(counter.countProperty().get()),
                counter.countProperty()));

        badgeListener = new ChatConnection.Listener() {
            @Override
            public void onMessage(MessageApiService.MessageItem message) {
                // власні повідомлення (ехо від сервера) лічильник не змінюють
                if (!message.senderId().equals(SessionManager.getInstance().getUserId())) {
                    counter.refresh();
                }
            }
        };
        chat.addListener(badgeListener);

        counter.refresh();
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
        ChatConnection chat = ChatConnection.getInstance();
        chat.removeListener(badgeListener);
        chat.disconnect();
        UnreadCounter.getInstance().reset();

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

    public void showDialogs() {
        loadIntoContent("/fxml/dialogs.fxml");
    }

    public void showDialogsWith(Long userId, String displayName) {
        DialogsController controller = loadIntoContent("/fxml/dialogs.fxml");
        if (controller != null) {
            controller.openWith(userId, displayName);
        }
    }

    @FXML
    private void handleShowDialogs() {
        showDialogs();
    }
    public void showSearch(String query) {
        SearchController controller = loadIntoContent("/fxml/search.fxml");
        if (controller != null) {
            controller.setShell(this);
            controller.runSearch(query);
        }
    }

    @FXML
    private void handleSearch() {
        String query = searchField.getText().trim();
        if (!query.isEmpty()) {
            showSearch(query);
        }
    }

}