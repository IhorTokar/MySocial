package com.example.social.client.component;

import com.example.social.client.service.ApiClient;
import com.example.social.client.service.NotificationApiService;
import com.example.social.client.util.AvatarUtil;
import com.example.social.client.util.ThemeManager;
import com.example.social.client.util.TimeAgo;
import com.example.social.shared.dto.NotificationDto;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Popup;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.function.Consumer;

/**
 * Дзвіночок у шапці: лічильник непрочитаних (оновлюється раз на хвилину) і випадний список.
 * Відкриття списку позначає все прочитаним; клік по сповіщенню — профіль (підписка) або пост.
 */
public class NotificationBell extends StackPane {

    private static final int LIST_LIMIT = 30;
    private static final Duration POLL_INTERVAL = Duration.minutes(1);

    private final NotificationApiService api = new NotificationApiService(new ApiClient());
    private final Consumer<Long> onOpenProfile;
    private final Consumer<Long> onOpenPost;

    private final Label badge = new Label();
    private final VBox itemsBox = new VBox(2);
    private final Popup popup = new Popup();
    private final Timeline poll;

    public NotificationBell(Consumer<Long> onOpenProfile, Consumer<Long> onOpenPost) {
        this.onOpenProfile = onOpenProfile;
        this.onOpenPost = onOpenPost;

        Button bell = new Button(null, new FontIcon("far-bell"));
        bell.getStyleClass().addAll("action-button", "notification-bell");
        bell.setTooltip(new Tooltip("Сповіщення"));
        bell.setOnAction(e -> togglePopup());

        badge.getStyleClass().add("unread-badge");
        badge.setMouseTransparent(true);
        badge.setVisible(false);
        StackPane.setAlignment(badge, Pos.TOP_RIGHT);

        getChildren().addAll(bell, badge);
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        buildPopup();
        poll = new Timeline(new KeyFrame(POLL_INTERVAL, e -> refreshCount()));
        poll.setCycleCount(Timeline.INDEFINITE);
        poll.play();
        refreshCount();
    }

    /** Зупинити опитування сервера (вихід з акаунта). */
    public void stop() {
        poll.stop();
        popup.hide();
    }

    public void refreshCount() {
        Thread.ofVirtual().start(() -> {
            try {
                long count = api.unreadCount();
                Platform.runLater(() -> showBadge(count));
            } catch (Exception ignored) {
                // сервер недоступний — спробуємо за хвилину
            }
        });
    }

    private void showBadge(long count) {
        badge.setText(count > 9 ? "9+" : String.valueOf(count));
        badge.setVisible(count > 0);
    }

    // ===== Випадний список =====

    private void buildPopup() {
        Label title = new Label("Сповіщення");
        title.getStyleClass().add("post-title");

        ScrollPane scroll = new ScrollPane(itemsBox);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMaxHeight(460);
        scroll.getStyleClass().add("discover-sidebar");   // прозорий фон, як у правій колонці

        VBox card = new VBox(8, title, scroll);
        card.getStyleClass().add("notifications-popup");
        card.setPrefWidth(380);

        popup.getContent().add(card);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
    }

    private void togglePopup() {
        if (popup.isShowing()) {
            popup.hide();
            return;
        }
        ThemeManager.getInstance().applyTo(popup.getScene());   // Popup не успадковує стилі вікна
        popup.getScene().getRoot().setStyle("-fx-background-color: transparent;");   // без квадратних кутів за карткою
        showPlaceholder("Завантаження...");
        Bounds b = localToScreen(getBoundsInLocal());
        popup.show(this, b.getMaxX() - 380, b.getMaxY() + 6);

        Thread.ofVirtual().start(() -> {
            try {
                List<NotificationDto> items = api.latest(LIST_LIMIT);
                api.markSeen();   // список відкрито — усе прочитано
                Platform.runLater(() -> {
                    showItems(items);
                    showBadge(0);
                });
            } catch (Exception e) {
                Platform.runLater(() -> showPlaceholder("Не вдалося завантажити сповіщення"));
            }
        });
    }

    private void showItems(List<NotificationDto> items) {
        if (items.isEmpty()) {
            showPlaceholder("Поки що сповіщень немає. Тут з'являться нові читачі, вподобання й коментарі.");
            return;
        }
        itemsBox.getChildren().setAll(items.stream().map(this::itemRow).toList());
    }

    private Node itemRow(NotificationDto n) {
        String name = n.actorDisplayName() == null || n.actorDisplayName().isBlank()
                ? n.actorUsername() : n.actorDisplayName();

        StackPane avatar = AvatarUtil.create(name, n.actorAvatarUrl(), 36);
        FontIcon typeIcon = new FontIcon(switch (n.type()) {
            case "follow" -> "fas-user-plus";
            case "like" -> "fas-heart";
            case "reply" -> "fas-reply";
            default -> "fas-comment";
        });
        typeIcon.getStyleClass().addAll("notification-icon", "notification-icon-" + n.type());
        StackPane.setAlignment(typeIcon, Pos.BOTTOM_RIGHT);
        StackPane avatarWithIcon = new StackPane(avatar, typeIcon);

        TextFlow text = new TextFlow();
        String quote = n.snippet() == null ? "" : "«" + n.snippet().strip() + "»";
        switch (n.type()) {
            case "follow" -> text.getChildren().addAll(bold(name), plain(" тепер читає вас"));
            case "like" -> text.getChildren().addAll(plain("Ваш пост " + quote + " сподобався "), bold(name));
            case "reply" -> text.getChildren().addAll(bold(name), plain(" відповідає на ваш коментар: " + quote));
            default -> text.getChildren().addAll(bold(name), plain(" коментує ваш пост: " + quote));
        }
        Label time = new Label(TimeAgo.format(n.createdAt()));
        time.getStyleClass().add("discover-meta");

        VBox body = new VBox(2, text, time);
        HBox.setHgrow(body, Priority.ALWAYS);
        body.setMinWidth(0);

        HBox row = new HBox(10, avatarWithIcon, body);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("notification-row");
        if (n.unread()) {
            Circle dot = new Circle(4);
            dot.getStyleClass().add("notification-dot");
            row.getChildren().add(dot);
            row.getStyleClass().add("notification-unread");
        }
        row.setCursor(Cursor.HAND);
        row.setOnMouseClicked(e -> {
            popup.hide();
            if (n.postId() == null) {
                onOpenProfile.accept(n.actorId());
            } else {
                onOpenPost.accept(n.postId());
            }
        });
        return row;
    }

    private void showPlaceholder(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("discover-meta");
        label.setWrapText(true);
        itemsBox.getChildren().setAll(label);
    }

    private static Text bold(String value) {
        Text t = new Text(value);
        t.getStyleClass().add("notification-name");
        return t;
    }

    private static Text plain(String value) {
        Text t = new Text(value);
        t.getStyleClass().add("notification-text");
        return t;
    }
}