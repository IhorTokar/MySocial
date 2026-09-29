package com.example.social.client.controller;

import com.example.social.client.component.FollowListCell;
import com.example.social.client.service.ApiClient;
import com.example.social.client.service.FollowApiService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

public class FollowsController {

    @FXML private Label titleLabel;
    @FXML private Label statusLabel;
    @FXML private Button followingTabButton;
    @FXML private Button followersTabButton;
    @FXML private ListView<FollowApiService.UserSummary> listView;

    private final FollowApiService followApiService = new FollowApiService(new ApiClient());
    private MainShellController shell;
    private Long targetUserId;
    private boolean showingFollowing = true;

    public void setShell(MainShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void initialize() {
        listView.setCellFactory(list -> new FollowListCell(this::openProfile, this::handleFollowChanged));
    }

    /** @param initialTab "following" чи "followers" — з якого списку почати */
    public void setUserId(Long userId, String initialTab) {
        this.targetUserId = userId;
        showingFollowing = !"followers".equals(initialTab);
        updateTabStyles();
        load();
    }

    @FXML
    private void handleShowFollowing() {
        showingFollowing = true;
        updateTabStyles();
        load();
    }

    @FXML
    private void handleShowFollowers() {
        showingFollowing = false;
        updateTabStyles();
        load();
    }

    private void updateTabStyles() {
        followingTabButton.getStyleClass().setAll("button",
                showingFollowing ? "button-primary" : "button-secondary");
        followersTabButton.getStyleClass().setAll("button",
                !showingFollowing ? "button-primary" : "button-secondary");
        titleLabel.setText(showingFollowing ? "Підписки" : "Підписники");
    }

    private void load() {
        statusLabel.setText("Завантаження...");
        listView.getItems().clear();

        Thread.ofVirtual().start(() -> {
            try {
                FollowApiService.ListResult result = showingFollowing
                        ? followApiService.getFollowing(targetUserId)
                        : followApiService.getFollowers(targetUserId);

                Platform.runLater(() -> {
                    if (result.success()) {
                        listView.getItems().setAll(result.users());
                        statusLabel.setText(result.users().isEmpty() ? "Список порожній" : "");
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

    private void handleFollowChanged(Long userId, boolean nowFollowing) {
        // якщо я щойно відписався від когось у власному списку "Підписки" — прибираємо його зі списку одразу
        if (showingFollowing && !nowFollowing && userId != null
                && targetUserId.equals(com.example.social.client.util.SessionManager.getInstance().getUserId())) {
            listView.getItems().removeIf(u -> u.userId().equals(userId));
        }
    }

    private void openProfile(Long userId) {
        if (shell != null) {
            shell.showProfile(userId);
        }
    }
}