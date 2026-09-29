package com.example.social.client.component;

import com.example.social.client.service.ApiClient;
import com.example.social.client.service.FollowApiService;
import com.example.social.client.service.SearchApiService;
import com.example.social.client.util.AvatarUtil;
import com.example.social.client.util.SessionManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class UserSearchCell extends ListCell<SearchApiService.UserSummary> {

    private final FollowApiService followApiService = new FollowApiService(new ApiClient());
    private final Consumer<Long> onOpenProfile;

    private final javafx.scene.layout.StackPane avatarHolder = new javafx.scene.layout.StackPane();
    private final Label nameLabel = new Label();
    private final Label handleLabel = new Label();
    private final Button followButton = new Button();
    private final HBox row;

    private boolean following;

    public UserSearchCell(Consumer<Long> onOpenProfile) {
        this.onOpenProfile = onOpenProfile;

        nameLabel.getStyleClass().add("post-author-name");
        handleLabel.getStyleClass().add("post-handle");
        VBox names = new VBox(2, nameLabel, handleLabel);

        followButton.getStyleClass().addAll("button", "button-secondary");
        followButton.setOnAction(e -> handleFollowToggle());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row = new HBox(10, avatarHolder, names, spacer, followButton);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8));
        row.setCursor(Cursor.HAND);
        row.setOnMouseClicked(e -> {
            SearchApiService.UserSummary item = getItem();
            if (item != null && onOpenProfile != null) {
                onOpenProfile.accept(item.userId());
            }
        });
    }

    @Override
    protected void updateItem(SearchApiService.UserSummary item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setGraphic(null);
            return;
        }

        avatarHolder.getChildren().setAll(AvatarUtil.create(item.nameOrUsername(), item.avatarUrl(), 40));
        nameLabel.setText(item.nameOrUsername());
        handleLabel.setText("@" + item.username());

        boolean isSelf = item.userId().equals(SessionManager.getInstance().getUserId());
        following = item.followedByCurrentUser();
        followButton.setVisible(!isSelf);
        followButton.setManaged(!isSelf);
        updateFollowButtonText();

        setGraphic(row);
    }

    private void updateFollowButtonText() {
        followButton.setText(following ? "Відстежуєте" : "Підписатись");
    }

    private void handleFollowToggle() {
        SearchApiService.UserSummary item = getItem();
        if (item == null) {
            return;
        }
        Long userId = item.userId();
        boolean wasFollowing = following;
        followButton.setDisable(true);

        Thread.ofVirtual().start(() -> {
            try {
                FollowApiService.ActionResult result = wasFollowing
                        ? followApiService.unfollow(userId)
                        : followApiService.follow(userId);

                Platform.runLater(() -> {
                    followButton.setDisable(false);
                    if (result.success()) {
                        following = !wasFollowing;
                        updateFollowButtonText();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> followButton.setDisable(false));
            }
        });
    }
}