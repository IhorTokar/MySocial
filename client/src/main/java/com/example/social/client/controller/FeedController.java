package com.example.social.client.controller;

import com.example.social.client.component.PostListCell;
import com.example.social.client.service.ApiClient;
import com.example.social.client.service.PostApiService;
import com.example.social.client.util.SceneUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FeedController {

    @FXML
    private ListView<PostApiService.PostItem> postsListView;

    @FXML
    private Label statusLabel;

    @FXML
    private Button refreshButton;

    /** Скільки постів довантажується за раз. */
    private static final int PAGE_SIZE = 20;
    /** Довантаження починається, коли на екрані з'являється п'ятий пост від кінця. */
    private static final int PREFETCH_DISTANCE = 5;

    private final PostApiService postApiService = new PostApiService(new ApiClient());
    private MainShellController shell;

    private boolean loading;
    private boolean hasMore = true;
    /** Номер «покоління» стрічки: відповіді, що прийшли після «Оновити», відкидаються. */
    private int generation;

    @FXML
    private void initialize() {
        postsListView.setCellFactory(list -> {
            PostListCell cell = new PostListCell(this::openProfile);
            cell.setOnEditRequested(this::openEditForm);
            // ListView створює комірки лише для видимих рядків — тож зміна індексу комірки
            // означає, що відповідний пост з'явився на екрані
            cell.indexProperty().addListener((obs, old, index) -> maybeLoadMore(index.intValue()));
            return cell;
        });
        loadFeed();
    }

    private void openEditForm(PostApiService.PostItem post) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/create_post.fxml"));
            javafx.scene.Parent root = loader.load();

            CreatePostController controller = loader.getController();
            controller.setEditMode(post);
            controller.setOnPublished(this::loadFeed);

            javafx.stage.Stage modal = new javafx.stage.Stage();
            modal.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            modal.setTitle("Редагувати пост");
            modal.setScene(SceneUtil.create(root, 500, 420));
            modal.showAndWait();
        } catch (java.io.IOException e) {
            statusLabel.setText("Помилка відкриття редагування: " + e.getMessage());
        }
    }

    private void openProfile(Long userId) {
        if (shell != null) {
            shell.showProfile(userId);
        }
    }

    public void setShell(com.example.social.client.controller.MainShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void handleRefresh() {
        loadFeed();
    }


    private void loadFeed() {
        loadPage(true);
    }

    private void maybeLoadMore(int visibleIndex) {
        int size = postsListView.getItems().size();
        if (hasMore && !loading && size > 0 && visibleIndex >= size - PREFETCH_DISTANCE) {
            loadPage(false);
        }
    }

    /** @param reset true — стрічка з початку («Оновити»), false — наступна сторінка */
    private void loadPage(boolean reset) {
        if (loading && !reset) {
            return;
        }
        loading = true;
        if (reset) {
            generation++;
            hasMore = true;
            refreshButton.setDisable(true);
        }
        int requestGeneration = generation;
        int offset = reset ? 0 : postsListView.getItems().size();
        statusLabel.setText(reset ? "Завантаження..." : "Завантаження ще постів...");

        Thread.ofVirtual().start(() -> {
            try {
                PostApiService.FeedResult result = postApiService.getFeed(offset, PAGE_SIZE);
                Platform.runLater(() -> applyPage(result, reset, requestGeneration));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (requestGeneration != generation) {
                        return;
                    }
                    loading = false;
                    refreshButton.setDisable(false);
                    statusLabel.setText("Помилка з'єднання: " + e.getMessage());
                });
            }
        });
    }

    private void applyPage(PostApiService.FeedResult result, boolean reset, int requestGeneration) {
        if (requestGeneration != generation) {
            return;   // поки сторінка вантажилась, користувач натиснув «Оновити»
        }
        loading = false;
        refreshButton.setDisable(false);
        if (!result.success()) {
            statusLabel.setText(result.errorMessage());
            return;
        }

        List<PostApiService.PostItem> page = result.posts();
        int added;
        if (reset) {
            postsListView.getItems().setAll(page);
            added = page.size();
        } else {
            // MMR може трохи переставити пости між сторінками — повтори не додаємо
            Set<Long> shown = new HashSet<>();
            postsListView.getItems().forEach(p -> shown.add(p.postId()));
            List<PostApiService.PostItem> fresh = page.stream().filter(p -> shown.add(p.postId())).toList();
            postsListView.getItems().addAll(fresh);
            added = fresh.size();
        }
        hasMore = page.size() == PAGE_SIZE && added > 0;

        int total = postsListView.getItems().size();
        statusLabel.setText("Постів у стрічці: " + total + (hasMore ? "" : " · це все"));
    }
}