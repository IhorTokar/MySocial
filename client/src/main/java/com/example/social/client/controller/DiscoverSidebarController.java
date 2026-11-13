package com.example.social.client.controller;

import com.example.social.client.service.ApiClient;
import com.example.social.client.service.DiscoverApiService;
import com.example.social.client.service.FollowApiService;
import com.example.social.client.util.AvatarUtil;
import com.example.social.shared.dto.SuggestedUserDto;
import com.example.social.shared.dto.TrendingTagDto;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Права колонка: «Гарячі теми» (клік — пошук за тегом) і «Кого почитати»
 * (клік — профіль, «Читати» — підписка без переходу).
 */
public class DiscoverSidebarController {

    private static final int TRENDS_LIMIT = 8;
    private static final int PEOPLE_LIMIT = 5;

    @FXML private VBox trendsBox;
    @FXML private VBox peopleBox;

    private final DiscoverApiService discoverApi = new DiscoverApiService(new ApiClient());
    private final FollowApiService followApi = new FollowApiService(new ApiClient());
    private MainShellController shell;

    public void setShell(MainShellController shell) {
        this.shell = shell;
    }

    @FXML
    public void refresh() {
        showPlaceholder(trendsBox, "Завантаження...");
        showPlaceholder(peopleBox, "Завантаження...");

        Thread.ofVirtual().start(() -> {
            try {
                List<TrendingTagDto> trends = discoverApi.getTrends(TRENDS_LIMIT);
                Platform.runLater(() -> showTrends(trends));
            } catch (Exception e) {
                Platform.runLater(() -> showPlaceholder(trendsBox, "Не вдалося завантажити теми"));
            }
        });
        Thread.ofVirtual().start(() -> {
            try {
                List<SuggestedUserDto> people = discoverApi.getSuggestedPeople(PEOPLE_LIMIT);
                Platform.runLater(() -> showPeople(people));
            } catch (Exception e) {
                Platform.runLater(() -> showPlaceholder(peopleBox, "Не вдалося завантажити пропозиції"));
            }
        });
    }

    // ===== Гарячі теми =====

    private void showTrends(List<TrendingTagDto> trends) {
        if (trends.isEmpty()) {
            showPlaceholder(trendsBox, "Поки що немає постів із тегами");
            return;
        }
        List<Node> rows = new ArrayList<>();
        for (int i = 0; i < trends.size(); i++) {
            rows.add(trendRow(i + 1, trends.get(i)));
        }
        trendsBox.getChildren().setAll(rows);
    }

    private Node trendRow(int rank, TrendingTagDto trend) {
        Label rankLabel = new Label(rank + " · Гаряча тема");
        rankLabel.getStyleClass().add("discover-meta");

        Label tagLabel = new Label("#" + trend.tag());
        tagLabel.getStyleClass().add("discover-tag");

        Label stats = new Label(activityText(trend));
        stats.getStyleClass().add("discover-meta");

        VBox row = new VBox(1, rankLabel, tagLabel, stats);
        row.getStyleClass().add("discover-row");
        row.setCursor(Cursor.HAND);
        row.setOnMouseClicked(e -> {
            if (shell != null) {
                shell.showSearch("#" + trend.tag());
            }
        });
        return row;
    }

    private static String activityText(TrendingTagDto t) {
        List<String> parts = new ArrayList<>();
        if (t.posts() > 0) {
            parts.add(t.posts() + " " + plural(t.posts(), "пост", "пости", "постів"));
        }
        if (t.likes() > 0) {
            parts.add(t.likes() + " " + plural(t.likes(), "вподобання", "вподобання", "вподобань"));
        }
        if (t.comments() > 0) {
            parts.add(t.comments() + " " + plural(t.comments(), "коментар", "коментарі", "коментарів"));
        }
        return String.join(" · ", parts);
    }

    // ===== Кого почитати =====

    private void showPeople(List<SuggestedUserDto> people) {
        if (people.isEmpty()) {
            showPlaceholder(peopleBox, "Нових пропозицій поки немає");
            return;
        }
        peopleBox.getChildren().setAll(people.stream().map(this::personRow).toList());
    }

    private Node personRow(SuggestedUserDto user) {
        String name = user.displayName() == null || user.displayName().isBlank() ? user.username() : user.displayName();

        Node avatar = AvatarUtil.create(name, user.avatarUrl(), 40);

        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("post-author-name");
        Label usernameLabel = new Label("@" + user.username() + " · "
                + user.followersCount() + " " + plural(user.followersCount(), "читач", "читачі", "читачів"));
        usernameLabel.getStyleClass().add("discover-meta");
        Label reasonLabel = new Label(user.reason());
        reasonLabel.getStyleClass().add("discover-reason");
        reasonLabel.setWrapText(true);

        VBox text = new VBox(1, nameLabel, usernameLabel, reasonLabel);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        // клік по аватару чи імені — профіль
        HBox clickable = new HBox(10, avatar, text);
        clickable.setAlignment(Pos.CENTER_LEFT);
        clickable.setCursor(Cursor.HAND);
        clickable.setOnMouseClicked(e -> {
            if (shell != null) {
                shell.showProfile(user.userId());
            }
        });
        HBox.setHgrow(clickable, Priority.ALWAYS);

        Button followButton = new Button("Читати");
        followButton.getStyleClass().setAll("button", "button-primary", "discover-follow");
        followButton.setMinWidth(Region.USE_PREF_SIZE);
        followButton.setOnAction(e -> follow(user, followButton));

        HBox row = new HBox(8, clickable, followButton);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("discover-row");
        return row;
    }

    private void follow(SuggestedUserDto user, Button button) {
        button.setDisable(true);
        Thread.ofVirtual().start(() -> {
            try {
                FollowApiService.ActionResult result = followApi.follow(user.userId());
                Platform.runLater(() -> {
                    if (result.success()) {
                        button.setText("Ви читаєте");
                        button.getStyleClass().setAll("button", "button-secondary", "discover-follow");
                    } else {
                        button.setDisable(false);
                        button.setTooltip(new Tooltip(result.message()));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    button.setDisable(false);
                    button.setTooltip(new Tooltip("Помилка з'єднання: " + e.getMessage()));
                });
            }
        });
    }

    // ===== Допоміжне =====

    private static void showPlaceholder(VBox box, String text) {
        Label label = new Label(text);
        label.getStyleClass().add("discover-meta");
        label.setWrapText(true);
        box.getChildren().setAll(label);
    }

    /** Українські форми: 1 пост, 2 пости, 5 постів, 21 пост, 12 постів. */
    static String plural(long n, String one, String few, String many) {
        long mod10 = n % 10;
        long mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) {
            return one;
        }
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            return few;
        }
        return many;
    }
}