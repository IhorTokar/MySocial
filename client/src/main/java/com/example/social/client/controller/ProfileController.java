package com.example.social.client.controller;

import com.example.social.client.component.PostListCell;
import com.example.social.client.service.ApiClient;
import com.example.social.client.service.FollowApiService;
import com.example.social.client.service.PostApiService;
import com.example.social.client.service.UserApiService;
import com.example.social.client.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class ProfileController {

    @FXML private ImageView avatarImageView;
    @FXML private Label displayNameLabel;
    @FXML private Label usernameHandleLabel;
    @FXML private Label aboutMeLabel;
    @FXML private Label postsCountLabel;
    @FXML private Label followersCountLabel;
    @FXML private Label followingCountLabel;
    @FXML private Button followButton;
    @FXML private Button editProfileButton;
    @FXML private Label statusLabel;
    @FXML private ListView<PostApiService.PostItem> postsListView;
    @FXML private Button backButton;

    private MainShellController shell;

    private final UserApiService userApiService = new UserApiService(new ApiClient());
    private final PostApiService postApiService = new PostApiService(new ApiClient());
    private final FollowApiService followApiService = new FollowApiService(new ApiClient());

    private Long profileUserId;
    private boolean isFollowing;

    public void setShell(MainShellController shell) {
        this.shell = shell;
    }

    public void setUserId(Long userId) {
        this.profileUserId = userId;
        postsListView.setCellFactory(list -> new PostListCell());
        loadProfile();
        loadPosts();
    }

    private boolean isOwnProfile() {
        Long myId = SessionManager.getInstance().getUserId();
        return myId != null && myId.equals(profileUserId);
    }

    private void loadProfile() {
        Thread.ofVirtual().start(() -> {
            try {
                UserApiService.UserProfileResult result = userApiService.getUser(profileUserId);

                Platform.runLater(() -> {
                    if (!result.success()) {
                        statusLabel.getStyleClass().setAll("error-label");
                        statusLabel.setText(result.errorMessage());
                        return;
                    }
                    applyProfile(result.profile());
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.getStyleClass().setAll("error-label");
                    statusLabel.setText("Помилка завантаження профілю: " + e.getMessage());
                });
            }
        });
    }

    public void applyProfile(UserApiService.UserProfile profile) {
        displayNameLabel.setText(profile.displayNameOrUsername());
        usernameHandleLabel.setText("@" + profile.username());
        aboutMeLabel.setText(profile.aboutMe() != null && !profile.aboutMe().isBlank()
                ? profile.aboutMe() : "Опис профілю відсутній");

        postsCountLabel.setText(profile.postsCount() + " " + pluralPosts(profile.postsCount()));
        followersCountLabel.setText(profile.followersCount() + " " + pluralFollowers(profile.followersCount()));
        followingCountLabel.setText(profile.followingCount() + " відстежує");

        if (profile.userAvatarUrl() != null && !profile.userAvatarUrl().isBlank()) {
            try {
                avatarImageView.setImage(new Image(ApiClient.BASE_URL + profile.userAvatarUrl(), true));
            } catch (Exception ignored) {
                // лишаємо плейсхолдер, якщо аватар не вдалось завантажити
            }
        }

        isFollowing = profile.followedByCurrentUser();

        if (isOwnProfile()) {
            followButton.setVisible(false);
            followButton.setManaged(false);
            editProfileButton.setVisible(true);
            editProfileButton.setManaged(true);
        } else {
            editProfileButton.setVisible(false);
            editProfileButton.setManaged(false);
            followButton.setVisible(true);
            followButton.setManaged(true);
            updateFollowButtonState();
        }
    }

    private void updateFollowButtonState() {
        if (isFollowing) {
            followButton.setText("Відстежуєте");
            followButton.getStyleClass().setAll("button", "button-secondary");
        } else {
            followButton.setText("Підписатись");
            followButton.getStyleClass().setAll("button", "button-primary");
        }
    }

    private void loadPosts() {
        Thread.ofVirtual().start(() -> {
            try {
                PostApiService.FeedResult postsResult = postApiService.getPostsByUser(profileUserId);
                Platform.runLater(() -> {
                    if (postsResult.success()) {
                        postsListView.getItems().setAll(postsResult.posts());
                    } else {
                        statusLabel.getStyleClass().setAll("error-label");
                        statusLabel.setText(postsResult.errorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.getStyleClass().setAll("error-label");
                    statusLabel.setText("Помилка завантаження постів: " + e.getMessage());
                });
            }
        });
    }

    @FXML
    private void handleFollowToggle() {
        followButton.setDisable(true);

        Thread.ofVirtual().start(() -> {
            try {
                FollowApiService.ActionResult result = isFollowing
                        ? followApiService.unfollow(profileUserId)
                        : followApiService.follow(profileUserId);

                Platform.runLater(() -> {
                    followButton.setDisable(false);
                    if (result.success()) {
                        isFollowing = !isFollowing;
                        updateFollowButtonState();
                        loadProfile();
                    } else {
                        statusLabel.getStyleClass().setAll("error-label");
                        statusLabel.setText(result.message());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    followButton.setDisable(false);
                    statusLabel.getStyleClass().setAll("error-label");
                    statusLabel.setText("Помилка з'єднання: " + e.getMessage());
                });
            }
        });
    }

    @FXML
    private void handleEditProfile() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/edit_profile.fxml"));
            javafx.scene.Parent root = loader.load();

            EditProfileController controller = loader.getController();
            controller.setParentProfileController(this);
            controller.prefill(displayNameLabel.getText(),
                    aboutMeLabel.getText().equals("Опис профілю відсутній") ? "" : aboutMeLabel.getText());

            javafx.stage.Stage editStage = new javafx.stage.Stage();
            editStage.setTitle("Редагувати профіль");
            editStage.setScene(new javafx.scene.Scene(root, 400, 350));
            editStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            editStage.initOwner(editProfileButton.getScene().getWindow());
            editStage.showAndWait();
        } catch (java.io.IOException e) {
            statusLabel.getStyleClass().setAll("error-label");
            statusLabel.setText("Помилка відкриття вікна редагування: " + e.getMessage());
        }
    }

    @FXML
    private void handleBack() {
        shell.showFeed();
    }

    private String pluralPosts(long count) {
        long mod10 = count % 10, mod100 = count % 100;
        if (mod10 == 1 && mod100 != 11) return "допис";
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "дописи";
        return "дописів";
    }

    private String pluralFollowers(long count) {
        long mod10 = count % 10, mod100 = count % 100;
        if (mod10 == 1 && mod100 != 11) return "підписник";
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "підписники";
        return "підписників";
    }
}