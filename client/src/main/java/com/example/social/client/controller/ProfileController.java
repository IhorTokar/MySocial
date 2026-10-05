package com.example.social.client.controller;

import com.example.social.client.component.PostListCell;
import com.example.social.client.service.ApiClient;
import com.example.social.client.service.FollowApiService;
import com.example.social.client.service.PostApiService;
import com.example.social.client.service.UserApiService;
import com.example.social.client.util.AvatarUtil;
import com.example.social.client.util.SceneUtil;
import com.example.social.client.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;

public class ProfileController {

    @FXML private StackPane avatarContainer;
    private UserApiService.UserProfile currentProfile;
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
    @FXML private Button messageButton;
    @FXML private javafx.scene.control.Button toggleCreatePostButton;
    @FXML private javafx.scene.layout.VBox createPost;
    @FXML private CreatePostController createPostController;
    @FXML private javafx.scene.layout.VBox communityCard;
    @FXML private Label communityInfoLabel;

    private final com.example.social.client.service.GraphApiService graphApiService =
            new com.example.social.client.service.GraphApiService(new ApiClient());
    private boolean createPostExpanded;

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
        postsListView.setCellFactory(list -> {
            PostListCell cell = new PostListCell(this::openProfile);
            cell.setOnEditRequested(this::openEditForm);
            return cell;
        });
        loadProfile();
        loadPosts();
        createPostController.setOnPublished(() -> {
            collapseCreatePost();
            loadPosts();
        });
        createPostController.setOnCancelled(this::collapseCreatePost);
    }

    private void openEditForm(PostApiService.PostItem post) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/create_post.fxml"));
            javafx.scene.Parent root = loader.load();

            CreatePostController controller = loader.getController();
            controller.setEditMode(post);
            controller.setOnPublished(this::loadPosts);

            javafx.stage.Stage modal = new javafx.stage.Stage();
            modal.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            modal.setTitle("Редагувати пост");
            modal.setScene(SceneUtil.create(root, 500, 420));
            modal.showAndWait();
        } catch (java.io.IOException e) {
            statusLabel.getStyleClass().setAll("error-label");
            statusLabel.setText("Помилка відкриття редагування: " + e.getMessage());
        }
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
        followersCountLabel.setCursor(javafx.scene.Cursor.HAND);
        followersCountLabel.setOnMouseClicked(e -> shell.showFollows(profileUserId, "followers"));

        followingCountLabel.setText(profile.followingCount() + " відстежує");
        followingCountLabel.setCursor(javafx.scene.Cursor.HAND);
        followingCountLabel.setOnMouseClicked(e -> shell.showFollows(profileUserId, "following"));

        currentProfile = profile;
        avatarContainer.getChildren().setAll(
                AvatarUtil.create(profile.displayNameOrUsername(), profile.userAvatarUrl(), 72));

        isFollowing = profile.followedByCurrentUser();

        if (isOwnProfile()) {
            followButton.setVisible(false);
            followButton.setManaged(false);
            editProfileButton.setVisible(true);
            editProfileButton.setManaged(true);
            messageButton.setVisible(false);
            messageButton.setManaged(false);
            toggleCreatePostButton.setVisible(true);
            toggleCreatePostButton.setManaged(true);
        } else {
            editProfileButton.setVisible(false);
            editProfileButton.setManaged(false);
            followButton.setVisible(true);
            followButton.setManaged(true);
            messageButton.setVisible(true);
            messageButton.setManaged(true);
            toggleCreatePostButton.setVisible(false);
            toggleCreatePostButton.setManaged(false);
            collapseCreatePost();
            updateFollowButtonState();
        }
        loadCommunityInfo();
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
        if (currentProfile == null) {
            return;
        }
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/edit_profile.fxml"));
            javafx.scene.Parent root = loader.load();

            EditProfileController controller = loader.getController();
            controller.setParentProfileController(this);
            controller.prefill(currentProfile);

            javafx.stage.Stage editStage = new javafx.stage.Stage();
            editStage.setTitle("Редагувати профіль");
            editStage.setScene(SceneUtil.create(root, 400, 450));
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

    @FXML
    private void handleWriteMessage() {
        shell.showDialogsWith(profileUserId, displayNameLabel.getText());
    }

    private void openProfile(Long userId) {
        if (shell != null) {
            shell.showProfile(userId);
        }
    }

    @FXML
    private void handleToggleCreatePost() {
        if (createPostExpanded) {
            collapseCreatePost();
        } else {
            createPostExpanded = true;
            createPost.setVisible(true);
            createPost.setManaged(true);
            toggleCreatePostButton.setText("Скасувати");
        }
    }

    private void collapseCreatePost() {
        createPostExpanded = false;
        createPost.setVisible(false);
        createPost.setManaged(false);
        toggleCreatePostButton.setText("Створити пост");
    }

    private void loadCommunityInfo() {
        if (communityCard == null) {
            return;
        }
        communityCard.setVisible(false);
        communityCard.setManaged(false);

        Thread.ofVirtual().start(() -> {
            try {
                var result = graphApiService.getUserCommunity(profileUserId);
                Platform.runLater(() -> {
                    if (!result.found()) {
                        return;
                    }
                    var info = result.info();
                    String tags = info.topTags().isEmpty() ? "—" : String.join(", ", info.topTags());
                    communityInfoLabel.setText(String.format(
                            "Спільнота з %d учасників · теми: %s · показник однорідності: %.0f%%",
                            info.memberCount(), tags, info.echoChamberScore() * 100));
                    communityCard.setVisible(true);
                    communityCard.setManaged(true);
                });
            } catch (Exception ignored) {
                // картка просто не показується, якщо дані недоступні
            }
        });
    }
}