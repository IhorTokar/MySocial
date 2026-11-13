package com.example.social.client.controller;

import com.example.social.client.util.SceneUtil;
import com.example.social.client.util.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import com.example.social.client.service.ApiClient;
import com.example.social.client.service.UserApiService;
import com.example.social.client.util.AvatarUtil;
import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseEvent;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.stage.Stage;
import com.example.social.client.service.ChatConnection;
import com.example.social.client.service.MessageApiService;
import com.example.social.client.service.UnreadCounter;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.ScrollPane;
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
    @FXML
    private javafx.scene.control.Button navAdminButton;
    @FXML
    private StackPane headerAvatar;
    @FXML
    private StackPane headerAvatar;
    @FXML
    private ScrollPane discoverSidebar;
    @FXML
    private DiscoverSidebarController discoverSidebarController;

    /** Права колонка потрібна лише там, де є місце й вона доречна (не в чатах / налаштуваннях / адмінці). */
    private static final double SIDEBAR_MIN_WINDOW_WIDTH = 1100;
    private final BooleanProperty sidebarAllowed = new SimpleBooleanProperty(true);

    private ChatConnection.Listener badgeListener;

    @FXML
    private void initialize() {
        ChatConnection chat = ChatConnection.getInstance();
        chat.connect();

        UnreadCounter counter = UnreadCounter.getInstance();

        unreadBadge.visibleProperty().bind(counter.countProperty().greaterThan(0));
        unreadBadge.managedProperty().bind(unreadBadge.visibleProperty());
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
        // Пункт «Адмін» бачить лише адміністратор (доступ однаково перевіряє сервер)
        boolean admin = SessionManager.getInstance().isAdmin();
        navAdminButton.setVisible(admin);
        navAdminButton.setManaged(admin);

        loadHeaderAvatar();

        discoverSidebarController.setShell(this);
        discoverSidebar.managedProperty().bind(discoverSidebar.visibleProperty());
        // сцена з'являється пізніше за initialize (і змінюється після виходу / входу)
        contentArea.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) {
                discoverSidebar.visibleProperty().bind(
                        sidebarAllowed.and(scene.widthProperty().greaterThan(SIDEBAR_MIN_WINDOW_WIDTH)));
            }
        });
        showFeed();
    }

    public void showFeed() {
        FeedController controller = loadIntoContent("/fxml/feed.fxml");
        if (controller != null) {
            controller.setShell(this);
        }
        discoverSidebarController.refresh();   // теми й пропозиції оновлюються при кожному поверненні до стрічки
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

    // ===== Аватар у шапці =====

    private void loadHeaderAvatar() {
        SessionManager session = SessionManager.getInstance();
        headerAvatar.getChildren().setAll(AvatarUtil.create(session.getUsername(), null, 34));   // поки вантажиться
        Thread.ofVirtual().start(() -> {
            try {
                UserApiService.UserProfileResult result = new UserApiService(new ApiClient()).getMyProfile();
                if (result.success()) {
                    Platform.runLater(() -> updateHeaderAvatar(result.profile()));
                }
            } catch (Exception ignored) {
                // лишається аватар із літерою — не критично
            }
        });
    }

    /** Викликається й після зміни фото / імені в профілі, щоб шапка одразу оновилась. */
    public void updateHeaderAvatar(UserApiService.UserProfile profile) {
        headerAvatar.getChildren().setAll(
                AvatarUtil.create(profile.displayNameOrUsername(), profile.userAvatarUrl(), 34));
    }

    @FXML
    private void handleHeaderAvatarClick(MouseEvent event) {
        MenuItem who = new MenuItem("@" + SessionManager.getInstance().getUsername());
        who.setDisable(true);
        MenuItem profile = new MenuItem("Мій профіль", new FontIcon("fas-user"));
        profile.setOnAction(e -> showMyProfile());
        MenuItem settings = new MenuItem("Налаштування", new FontIcon("fas-cog"));
        settings.setOnAction(e -> showSettings());
        MenuItem logout = new MenuItem("Вийти", new FontIcon("fas-sign-out-alt"));
        logout.setOnAction(e -> handleLogout());

        ContextMenu menu = new ContextMenu(who, new SeparatorMenuItem(), profile, settings, new SeparatorMenuItem(), logout);
        menu.show(headerAvatar, Side.BOTTOM, 0, 6);
    }

    // ===== Аватар у шапці =====

    private void loadHeaderAvatar() {
        SessionManager session = SessionManager.getInstance();
        headerAvatar.getChildren().setAll(AvatarUtil.create(session.getUsername(), null, 34));   // поки вантажиться
        Thread.ofVirtual().start(() -> {
            try {
                UserApiService.UserProfileResult result = new UserApiService(new ApiClient()).getMyProfile();
                if (result.success()) {
                    Platform.runLater(() -> updateHeaderAvatar(result.profile()));
                }
            } catch (Exception ignored) {
                // лишається аватар із літерою — не критично
            }
        });
    }

    /** Викликається й після зміни фото / імені в профілі, щоб шапка одразу оновилась. */
    public void updateHeaderAvatar(UserApiService.UserProfile profile) {
        headerAvatar.getChildren().setAll(
                AvatarUtil.create(profile.displayNameOrUsername(), profile.userAvatarUrl(), 34));
    }

    @FXML
    private void handleHeaderAvatarClick(MouseEvent event) {
        MenuItem who = new MenuItem("@" + SessionManager.getInstance().getUsername());
        who.setDisable(true);
        MenuItem profile = new MenuItem("Мій профіль", new FontIcon("fas-user"));
        profile.setOnAction(e -> showMyProfile());
        MenuItem settings = new MenuItem("Налаштування", new FontIcon("fas-cog"));
        settings.setOnAction(e -> showSettings());
        MenuItem logout = new MenuItem("Вийти", new FontIcon("fas-sign-out-alt"));
        logout.setOnAction(e -> handleLogout());

        ContextMenu menu = new ContextMenu(who, new SeparatorMenuItem(), profile, settings, new SeparatorMenuItem(), logout);
        menu.show(headerAvatar, Side.BOTTOM, 0, 6);
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
        showMyProfile();
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
            SceneUtil.switchScene(stage, root, 400, 400);
            stage.setTitle("Соціальна мережа");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private <T> T loadIntoContent(String fxmlPath) {
        return loadIntoContent(fxmlPath, true);
    }

    @SuppressWarnings("unchecked")
    private <T> T loadIntoContent(String fxmlPath, boolean withSidebar) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent node = loader.load();
            contentArea.getChildren().setAll(node);
            sidebarAllowed.set(withSidebar);
            return loader.getController();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void showDialogs() {
        loadIntoContent("/fxml/dialogs.fxml", false);   // у чатах права колонка не потрібна
    }

    public void showDialogsWith(Long userId, String displayName) {
        DialogsController controller = loadIntoContent("/fxml/dialogs.fxml", false);
        if (controller != null) {
            controller.openWith(userId, displayName);
        }
    }

    public void showFollows(Long userId, String initialTab) {
        FollowsController controller = loadIntoContent("/fxml/follows.fxml");
        if (controller != null) {
            controller.setShell(this);
            controller.setUserId(userId, initialTab);
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

    public void showSettings() {
        loadIntoContent("/fxml/settings.fxml", false);
    }

    @FXML
    private void handleShowSettings() {
        showSettings();
    }

    public void showAdmin() {
        AdminPanelController controller = loadIntoContent("/fxml/admin.fxml", false);
        if (controller != null) {
            controller.setShell(this);
        }
    }

    @FXML
    private void handleShowAdmin() {
        showAdmin();
    }

}