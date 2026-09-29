package com.example.social.client.component;

import com.example.social.client.service.ApiClient;
import com.example.social.client.service.LikeApiService;
import com.example.social.client.service.PostApiService;
import com.example.social.client.service.SavePostApiService;
import com.example.social.client.util.AvatarUtil;
import com.example.social.client.util.SessionManager;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class PostListCell extends ListCell<PostApiService.PostItem> {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.of("uk"));

    private static final Set<Long> LIKES_IN_FLIGHT = new HashSet<>();
    private static final Set<Long> SAVES_IN_FLIGHT = new HashSet<>();

    private final LikeApiService likeApiService = new LikeApiService(new ApiClient());
    private final SavePostApiService saveApiService = new SavePostApiService(new ApiClient());
    private final PostApiService postApiService = new PostApiService(new ApiClient());
    private final Consumer<Long> onOpenProfile;
    private Consumer<PostApiService.PostItem> onEditRequested;

    private final VBox card = new VBox(8);
    private final StackPane avatarHolder = new StackPane();
    private final Label nameLabel = new Label();
    private final Label handleLabel = new Label();
    private final Label dotLabel = new Label("·");
    private final Label dateLabel = new Label();
    private final Button moreButton = new Button("⋯");
    private final ContextMenu moreMenu = new ContextMenu();
    private final MenuItem editItem = new MenuItem("Редагувати");
    private final MenuItem deleteItem = new MenuItem("Видалити");
    private final Label titleLabel = new Label();
    private final Label textLabel = new Label();
    private final ImageView imageView = new ImageView();
    private final FlowPane tagsPane = new FlowPane(6, 4);
    private final Button likeButton = new Button();
    private final Button commentsButton = new Button();
    private final Button saveButton = new Button();
    private final Label errorLabel = new Label();
    private final FontIcon likeIcon = new FontIcon("far-heart");
    private final FontIcon commentIcon = new FontIcon("far-comment");
    private final FontIcon saveIcon = new FontIcon("far-bookmark");
    private HBox cardWrapper;
    private CommentThreadPane commentThread;
    private boolean commentsExpanded;

    private Integer renderedKey;

    public PostListCell() {
        this(null);
    }

    /** @param onOpenProfile викликається з id автора, коли клікнули на його аватар чи ім'я */
    public PostListCell(Consumer<Long> onOpenProfile) {
        this.onOpenProfile = onOpenProfile;
        setPrefWidth(0);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        buildCard();
    }

    public void setOnEditRequested(Consumer<PostApiService.PostItem> handler) {
        this.onEditRequested = handler;
    }

    private void buildCard() {
        card.getStyleClass().add("post-card");
        card.prefWidthProperty().bind(Bindings.min(widthProperty().subtract(26), 640));
        card.setMaxWidth(Region.USE_PREF_SIZE);

        cardWrapper = new HBox(card);
        cardWrapper.setAlignment(Pos.CENTER);
        cardWrapper.prefWidthProperty().bind(widthProperty());

        avatarHolder.setCursor(Cursor.HAND);
        avatarHolder.setOnMouseClicked(e -> openAuthorProfile());

        nameLabel.getStyleClass().add("post-author-name");
        nameLabel.setCursor(Cursor.HAND);
        nameLabel.setOnMouseClicked(e -> openAuthorProfile());
        handleLabel.getStyleClass().add("post-handle");
        dotLabel.getStyleClass().add("post-date");
        dateLabel.getStyleClass().add("post-date");

        HBox nameRow = new HBox(6, nameLabel, handleLabel, dotLabel, dateLabel);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        moreButton.getStyleClass().add("action-button");
        moreMenu.getItems().addAll(editItem, deleteItem);
        moreButton.setOnAction(e -> moreMenu.show(moreButton, Side.BOTTOM, 0, 0));
        editItem.setOnAction(e -> handleEdit());
        deleteItem.getStyleClass().add("menu-item-danger");
        deleteItem.setOnAction(e -> handleDelete());

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        HBox header = new HBox(12, avatarHolder, nameRow, headerSpacer, moreButton);
        header.setAlignment(Pos.CENTER_LEFT);

        titleLabel.getStyleClass().add("post-title");
        titleLabel.setWrapText(true);
        textLabel.getStyleClass().add("post-text");
        textLabel.setWrapText(true);

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setFitHeight(520);
        imageView.fitWidthProperty().bind(Bindings.min(card.widthProperty().subtract(34), 560));
        Rectangle clip = new Rectangle();
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        imageView.layoutBoundsProperty().addListener((obs, old, bounds) -> {
            clip.setWidth(bounds.getWidth());
            clip.setHeight(bounds.getHeight());
        });
        imageView.setClip(clip);

        likeButton.setGraphic(likeIcon);
        likeButton.getStyleClass().add("action-button");
        likeButton.setOnAction(e -> handleLike());

        commentsButton.setGraphic(commentIcon);
        commentsButton.getStyleClass().add("action-button");
        commentsButton.setOnAction(e -> handleComments());

        saveButton.setGraphic(saveIcon);
        saveButton.getStyleClass().add("action-button");
        saveButton.setOnAction(e -> handleSave());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(4, likeButton, commentsButton, spacer, saveButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getStyleClass().add("post-actions");

        errorLabel.getStyleClass().add("error-label");
        errorLabel.setWrapText(true);
        errorLabel.visibleProperty().bind(errorLabel.textProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        card.getChildren().addAll(header, titleLabel, textLabel, imageView, tagsPane, actions, errorLabel);
    }

    @Override
    protected void updateItem(PostApiService.PostItem post, boolean empty) {
        super.updateItem(post, empty);

        if (empty || post == null) {
            setText(null);
            setGraphic(null);
            renderedKey = null;
            return;
        }

        int key = Objects.hash(post.postId(), post.label(), post.text(), post.mediaUrl(),
                post.authorAvatarUrl(), post.authorNameOrUsername(), post.tags());
        if (renderedKey == null || renderedKey != key) {
            renderContent(post);
            renderedKey = key;
        }
        renderActions(post);

        setText(null);
        setGraphic(cardWrapper);
    }

    private void renderContent(PostApiService.PostItem post) {
        avatarHolder.getChildren().setAll(
                AvatarUtil.create(post.authorNameOrUsername(), post.authorAvatarUrl(), 44));
        nameLabel.setText(post.authorNameOrUsername());
        handleLabel.setText("@" + post.authorUsername());
        dateLabel.setText(formatDate(post.createdDate()));

        boolean isOwner = post.authorId() != null
                && post.authorId().equals(SessionManager.getInstance().getUserId());
        show(moreButton, isOwner);

        boolean hasTitle = post.label() != null && !post.label().isBlank();
        titleLabel.setText(hasTitle ? post.label() : "");
        show(titleLabel, hasTitle);

        textLabel.setText(post.text());

        String media = post.mediaUrl();
        if (media != null && !media.isBlank()) {
            try {
                String fullUrl = media.startsWith("http") ? media : ApiClient.BASE_URL + media;
                imageView.setImage(new Image(fullUrl, 900, 0, true, true, true));
                show(imageView, true);
            } catch (Exception e) {
                imageView.setImage(null);
                show(imageView, false);
            }
        } else {
            imageView.setImage(null);
            show(imageView, false);
        }

        tagsPane.getChildren().clear();
        for (String tag : post.tags()) {
            Label tagLabel = new Label("#" + tag);
            tagLabel.getStyleClass().add("post-tag");
            tagsPane.getChildren().add(tagLabel);
        }
        show(tagsPane, !post.tags().isEmpty());

        if (commentThread != null) {
            card.getChildren().remove(commentThread);
            commentThread = null;
        }
        commentsExpanded = false;
        errorLabel.setText("");
    }

    private void renderActions(PostApiService.PostItem post) {
        likeIcon.setIconLiteral(post.likedByCurrentUser() ? "fas-heart" : "far-heart");
        likeButton.setText(post.likesCount() > 0 ? String.valueOf(post.likesCount()) : "");
        setStyleFlag(likeButton, "like-active", post.likedByCurrentUser());

        commentsButton.setText(post.commentsCount() > 0 ? String.valueOf(post.commentsCount()) : "");

        saveIcon.setIconLiteral(post.savedByCurrentUser() ? "fas-bookmark" : "far-bookmark");
        setStyleFlag(saveButton, "save-active", post.savedByCurrentUser());
    }

    private void handleLike() {
        PostApiService.PostItem post = getItem();
        if (post == null) {
            return;
        }
        Long postId = post.postId();
        if (!LIKES_IN_FLIGHT.add(postId)) {
            return;
        }

        ListView<PostApiService.PostItem> list = getListView();
        boolean wasLiked = post.likedByCurrentUser();
        long oldCount = post.likesCount();
        long newCount = Math.max(0, oldCount + (wasLiked ? -1 : 1));

        errorLabel.setText("");
        updateInList(list, postId, p -> p.withLike(!wasLiked, newCount));

        Thread.ofVirtual().start(() -> {
            String error = null;
            try {
                LikeApiService.ActionResult result = wasLiked
                        ? likeApiService.unlike(postId)
                        : likeApiService.like(postId);
                if (!result.success()) {
                    error = result.errorMessage();
                }
            } catch (Exception ex) {
                error = "Помилка з'єднання";
            }
            String failure = error;

            Platform.runLater(() -> {
                LIKES_IN_FLIGHT.remove(postId);
                if (failure != null) {
                    updateInList(list, postId, p -> p.withLike(wasLiked, oldCount));
                    showError(postId, failure);
                }
            });
        });
    }

    private void handleSave() {
        PostApiService.PostItem post = getItem();
        if (post == null) {
            return;
        }
        Long postId = post.postId();
        if (!SAVES_IN_FLIGHT.add(postId)) {
            return;
        }

        ListView<PostApiService.PostItem> list = getListView();
        boolean wasSaved = post.savedByCurrentUser();

        errorLabel.setText("");
        updateInList(list, postId, p -> p.withSaved(!wasSaved));

        Thread.ofVirtual().start(() -> {
            String error = null;
            try {
                SavePostApiService.ActionResult result = wasSaved
                        ? saveApiService.unsave(postId)
                        : saveApiService.save(postId);
                if (!result.success()) {
                    error = result.errorMessage();
                }
            } catch (Exception ex) {
                error = "Помилка з'єднання";
            }
            String failure = error;

            Platform.runLater(() -> {
                SAVES_IN_FLIGHT.remove(postId);
                if (failure != null) {
                    updateInList(list, postId, p -> p.withSaved(wasSaved));
                    showError(postId, failure);
                }
            });
        });
    }

    private void handleComments() {
        PostApiService.PostItem post = getItem();
        if (post == null) {
            return;
        }
        Long postId = post.postId();
        ListView<PostApiService.PostItem> list = getListView();

        commentsExpanded = !commentsExpanded;

        if (commentsExpanded) {
            if (commentThread == null) {
                commentThread = new CommentThreadPane(postId, delta ->
                        updateInList(list, postId, p -> p.withCommentsCount(p.commentsCount() + delta)));
                card.getChildren().add(commentThread);
            }
            commentThread.setVisible(true);
            commentThread.setManaged(true);
            commentThread.ensureLoaded();
        } else if (commentThread != null) {
            commentThread.setVisible(false);
            commentThread.setManaged(false);
        }
    }

    private void handleEdit() {
        PostApiService.PostItem post = getItem();
        if (post != null && onEditRequested != null) {
            onEditRequested.accept(post);
        }
    }

    private void handleDelete() {
        PostApiService.PostItem post = getItem();
        if (post == null) {
            return;
        }
        Long postId = post.postId();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Видалити цей пост? Дію неможливо скасувати.");
        confirm.setHeaderText(null);
        var result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        ListView<PostApiService.PostItem> list = getListView();

        Thread.ofVirtual().start(() -> {
            try {
                PostApiService.ActionResult deleteResult = postApiService.deletePost(postId);
                Platform.runLater(() -> {
                    if (deleteResult.success() && list != null) {
                        list.getItems().removeIf(p -> p.postId().equals(postId));
                    } else if (!deleteResult.success()) {
                        showError(postId, deleteResult.errorMessage());
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> showError(postId, "Помилка з'єднання"));
            }
        });
    }

    private void openAuthorProfile() {
        PostApiService.PostItem post = getItem();
        if (post != null && onOpenProfile != null) {
            onOpenProfile.accept(post.authorId());
        }
    }

    private static void updateInList(ListView<PostApiService.PostItem> list, Long postId,
                                     UnaryOperator<PostApiService.PostItem> change) {
        if (list == null) {
            return;
        }
        ObservableList<PostApiService.PostItem> items = list.getItems();
        for (int i = 0; i < items.size(); i++) {
            PostApiService.PostItem current = items.get(i);
            if (current.postId().equals(postId)) {
                items.set(i, change.apply(current));
                return;
            }
        }
    }

    private void showError(Long postId, String message) {
        PostApiService.PostItem current = getItem();
        if (current != null && current.postId().equals(postId)) {
            errorLabel.setText(message);
        }
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static void setStyleFlag(Button button, String styleClass, boolean on) {
        button.getStyleClass().remove(styleClass);
        if (on) {
            button.getStyleClass().add(styleClass);
        }
    }

    private String formatDate(String isoDate) {
        try {
            return LocalDateTime.parse(isoDate).format(DISPLAY_FORMAT);
        } catch (Exception e) {
            return isoDate;
        }
    }
}