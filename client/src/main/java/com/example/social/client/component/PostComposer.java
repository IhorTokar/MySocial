package com.example.social.client.component;

import com.example.social.client.controller.CreatePostController;
import com.example.social.client.service.ApiClient;
import com.example.social.client.service.UserApiService;
import com.example.social.client.util.AvatarUtil;
import com.example.social.client.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

/**
 * Поле «Що у вас нового?» над стрічкою. Згорнуте — один рядок з аватаром;
 * клік розгортає звичайну форму створення поста (create_post.fxml), після публікації — згортається.
 */
public class PostComposer extends VBox {

    private final StackPane avatarHolder = new StackPane();
    private final HBox collapsedBar;
    private final Node form;
    private final CreatePostController formController;
    private Consumer<Long> onPublished;

    public PostComposer() {
        Label prompt = new Label("Що у вас нового?");
        prompt.getStyleClass().add("composer-prompt");
        prompt.setMaxWidth(Double.MAX_VALUE);
        prompt.setCursor(Cursor.TEXT);
        prompt.setOnMouseClicked(e -> expand(false));
        HBox.setHgrow(prompt, Priority.ALWAYS);

        Button photoButton = new Button(null, new FontIcon("far-image"));
        photoButton.getStyleClass().add("action-button");
        photoButton.setTooltip(new Tooltip("Пост із фото"));
        photoButton.setOnAction(e -> expand(true));

        collapsedBar = new HBox(12, avatarHolder, prompt, photoButton);
        collapsedBar.setAlignment(Pos.CENTER_LEFT);
        collapsedBar.getStyleClass().addAll("post-card", "composer-bar");

        try {
            FXMLLoader loader = new FXMLLoader(PostComposer.class.getResource("/fxml/create_post.fxml"));
            form = loader.load();
            formController = loader.getController();
        } catch (IOException e) {
            throw new UncheckedIOException("Не вдалося завантажити форму поста", e);
        }
        formController.setOnCancelled(this::collapse);
        formController.setOnPostPublished(postId -> {
            collapse();
            if (onPublished != null) {
                onPublished.accept(postId);
            }
        });

        getChildren().setAll(collapsedBar);
        loadAvatar();
    }

    /** Викликається з id нового поста — стрічка покаже його вгорі. */
    public void setOnPublished(Consumer<Long> onPublished) {
        this.onPublished = onPublished;
    }

    /** @param chooseImage одразу відкрити вибір фото */
    public void expand(boolean chooseImage) {
        getChildren().setAll(form);
        Platform.runLater(() -> {
            formController.focusText();
            if (chooseImage) {
                formController.chooseImage();
            }
        });
    }

    public void collapse() {
        getChildren().setAll(collapsedBar);
    }

    private void loadAvatar() {
        SessionManager session = SessionManager.getInstance();
        avatarHolder.getChildren().setAll(AvatarUtil.create(session.getUsername(), null, 40));
        Thread.ofVirtual().start(() -> {
            try {
                UserApiService.UserProfileResult result = new UserApiService(new ApiClient()).getMyProfile();
                if (result.success()) {
                    Platform.runLater(() -> avatarHolder.getChildren().setAll(AvatarUtil.create(
                            result.profile().displayNameOrUsername(), result.profile().userAvatarUrl(), 40)));
                }
            } catch (Exception ignored) {
                // лишається аватар із літерою
            }
        });
    }
}