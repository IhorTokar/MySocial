package com.example.social.client.component;

import com.example.social.client.service.ApiClient;
import com.example.social.client.service.FollowApiService;
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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class FollowListCell extends ListCell<FollowApiService.UserSummary> {

    private final FollowApiService followApiService = new FollowApiService(new ApiClient());
    private final Consumer<Long> onOpenProfile;
    private final BiConsumer<Long, Boolean> onFollowChanged; // (userId, тепер підписаний?)

    private final StackPane avatarHolder = new StackPane();
    private final Label nameLabel = new Label();
    private final Label handleLabel = new Label();
    private final Button followButton = new Button();
    private final HBox row;

    private boolean following;

    /**
     * @param onOpenProfile викликається з id при кліку по рядку
     * @param onFollowChanged викликається після успішної зміни підписки — щоб батьківський
     *                        екран міг, наприклад, прибрати юзера зі списку "Підписки" при відписці
     */
    public FollowListCell(Consumer<Long> onOpenProfile, BiConsumer<Long, Boolean> onFollowChanged) {
        this.onOpenProfile = onOpenProfile;
        this.onFollowChanged = onFollowChanged;

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
            if (e.getTarget() == followButton || followButton.getBoundsInParent().contains(e.getX(), e.getY())) {
                return; // клік по кнопці не має відкривати профіль
            }
            FollowApiService.UserSummary item = getItem();
            if (item != null && onOpenProfile != null) {
                onOpenProfile.accept(item.userId());
            }
        });
    }

    @Override
    protected void updateItem(FollowApiService.UserSummary item, boolean empty) {
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
        followButton.setText(following ? "Відписатись" : "Підписатись");
    }

    private void handleFollowToggle() {
        FollowApiService.UserSummary item = getItem();
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
                        if (onFollowChanged != null) {
                            onFollowChanged.accept(userId, following);
                        }
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> followButton.setDisable(false));
            }
        });
    }
}