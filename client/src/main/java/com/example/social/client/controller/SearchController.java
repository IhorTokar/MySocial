package com.example.social.client.controller;

import com.example.social.client.component.PostListCell;
import com.example.social.client.component.UserSearchCell;
import com.example.social.client.service.ApiClient;
import com.example.social.client.service.PostApiService;
import com.example.social.client.service.SearchApiService;
import com.example.social.client.util.SceneUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

public class SearchController {

    @FXML private Label queryLabel;
    @FXML private Label statusLabel;
    @FXML private ToggleButton textModeButton;
    @FXML private ToggleButton semanticModeButton;
    @FXML private Label modeHintLabel;
    @FXML private Label usersHeaderLabel;
    @FXML private ListView<SearchApiService.UserSummary> usersListView;
    @FXML private Label postsHeaderLabel;
    @FXML private ListView<PostApiService.PostItem> postsListView;

    /** Висота рядка користувача (аватар 40 + відступи) — щоб список займав рівно стільки місця, скільки рядків. */
    private static final double USER_ROW_HEIGHT = 64;
    /** Більше рядків — з'являється прокрутка всередині списку. */
    private static final int MAX_VISIBLE_USERS = 4;

    private final SearchApiService searchApiService = new SearchApiService(new ApiClient());
    private MainShellController shell;
    /** Останній обраний спосіб пошуку постів — зберігається між пошуками в межах сеансу. */
    private static boolean semanticMode;
    private String currentQuery;

    public void setShell(MainShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void initialize() {
        usersListView.setCellFactory(list -> new UserSearchCell(this::openProfile));
        usersListView.setFixedCellSize(USER_ROW_HEIGHT);
        postsListView.setCellFactory(list -> {
            PostListCell cell = new PostListCell(this::openProfile);
            cell.setOnEditRequested(this::openEditForm);
            return cell;
        });

        ToggleGroup mode = new ToggleGroup();
        textModeButton.setToggleGroup(mode);
        semanticModeButton.setToggleGroup(mode);
        (semanticMode ? semanticModeButton : textModeButton).setSelected(true);
        updateModeHint();
        mode.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                old.setSelected(true);   // один зі способів обрано завжди
                return;
            }
            boolean semantic = selected == semanticModeButton;
            if (semantic == semanticMode) {
                return;   // повторне обрання того самого способу (після скасованого «зняття»)
            }
            semanticMode = semantic;
            updateModeHint();
            if (currentQuery != null) {
                runSearch(currentQuery);
            }
        });
    }

    private void updateModeHint() {
        modeHintLabel.setText(semanticMode
                ? "пости, близькі за змістом, навіть без спільних слів (напр. «де смачно поїсти» знайде пост про ресторан)"
                : "пости, де є ці слова, або #тег");
    }

    public void runSearch(String query) {
        currentQuery = query;
        boolean semantic = semanticMode;
        queryLabel.setText("Результати пошуку: \"" + query + "\"");
        statusLabel.setText("Пошук...");
        usersListView.getItems().clear();
        postsListView.getItems().clear();
        // поки йде пошук, порожні секції не показуються
        showSection(false, usersHeaderLabel, usersListView);
        showSection(false, postsHeaderLabel, postsListView);

        Thread.ofVirtual().start(() -> {
            try {
                SearchApiService.SearchResult result = searchApiService.search(query, semantic);

                Platform.runLater(() -> {
                    if (result.success()) {
                        usersListView.getItems().setAll(result.users());
                        postsListView.getItems().setAll(result.posts());
                        usersHeaderLabel.setText("Користувачі (" + result.users().size() + ")");
                        postsHeaderLabel.setText("Пости (" + result.posts().size() + ")");

                        // секція користувачів — лише якщо когось знайдено (пошук за #тегом людей не шукає),
                        // і рівно під кількість рядків, а не фіксовані 220 px
                        int users = result.users().size();
                        usersListView.setPrefHeight(Math.min(users, MAX_VISIBLE_USERS) * USER_ROW_HEIGHT + 2);
                        showSection(users > 0, usersHeaderLabel, usersListView);
                        showSection(!result.posts().isEmpty(), postsHeaderLabel, postsListView);
                        statusLabel.getStyleClass().setAll("secondary-label");
                        if ("semantic-unavailable".equals(result.mode())) {
                            statusLabel.setText("Пошук за змістом зараз недоступний (сервіс обробки тексту не відповідає) — "
                                    + "показано пошук за словами.");
                        } else if (result.users().isEmpty() && result.posts().isEmpty()) {
                            statusLabel.setText(semantic && !query.startsWith("#")
                                    ? "Нічого близького за змістом не знайдено" : "Нічого не знайдено");
                        } else {
                            statusLabel.setText("semantic".equals(result.mode())
                                    ? "Пости впорядковано від найближчого за змістом" : "");
                        }
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

    private static void showSection(boolean visible, Node... nodes) {
        for (Node node : nodes) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    private void openProfile(Long userId) {
        if (shell != null) {
            shell.showProfile(userId);
        }
    }

    private void openEditForm(PostApiService.PostItem post) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/create_post.fxml"));
            javafx.scene.Parent root = loader.load();

            CreatePostController controller = loader.getController();
            controller.setEditMode(post);

            javafx.stage.Stage modal = new javafx.stage.Stage();
            modal.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            modal.setTitle("Редагувати пост");
            modal.setScene(SceneUtil.create(root, 500, 420));
            modal.showAndWait();
        } catch (java.io.IOException e) {
            statusLabel.setText("Помилка відкриття редагування: " + e.getMessage());
        }
    }
}