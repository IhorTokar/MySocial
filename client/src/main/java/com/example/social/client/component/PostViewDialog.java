package com.example.social.client.component;

import com.example.social.client.service.ApiClient;
import com.example.social.client.service.PostApiService;
import com.example.social.client.util.SceneUtil;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Один пост в окремому вікні (клік по картці або сповіщення): та сама картка, що й у стрічці,
 * з одразу розгорнутими коментарями, а під нею — «Схожі за змістом» (найближчі за вектором тексту).
 *
 * <p>Вікно поста завжди одне: якщо воно вже відкрите (напр., клік по схожому посту), у ньому
 * просто показується інший пост. Зі схожих видно один, решта — за кнопкою «Ще». Esc закриває вікно.
 */
public final class PostViewDialog {

    private static final int SIMILAR = 5;
    private static final int SNIPPET = 110;

    /** Єдине відкрите вікно поста (null — закрите). */
    private static PostViewDialog open;

    private final Stage stage = new Stage();
    private final ListView<PostApiService.PostItem> list = new ListView<>();
    private final Label similarHeader = new Label("Схожі за змістом");
    private final Button moreButton = new Button();
    private final VBox firstSimilar = new VBox();
    private final VBox moreSimilar = new VBox(6);
    private final VBox similarSection;

    private Consumer<Long> onOpenProfile = id -> { };
    private Consumer<PostApiService.PostItem> onChanged = p -> { };
    /** Номер завантаження схожих: відповідь для попереднього поста ігнорується. */
    private int similarRequest;
    /** Іде заміна поста у вікні — це не зміна, про яку треба повідомляти стрічку. */
    private boolean replacing;

    private PostViewDialog(Window owner) {
        list.getStyleClass().add("list-view");
        list.setCellFactory(l -> new PostListCell(userId -> {
            stage.close();
            onOpenProfile.accept(userId);
        }).inDialog());
        list.getItems().addListener((ListChangeListener<PostApiService.PostItem>) change -> {
            while (change.next()) {
                if (!replacing && (change.wasReplaced() || change.wasAdded())) {
                    change.getAddedSubList().forEach(p -> onChanged.accept(p));
                }
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        similarHeader.getStyleClass().add("post-title");
        moreButton.getStyleClass().addAll("button", "button-secondary");
        moreButton.setOnAction(e -> toggleMore());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, similarHeader, spacer, moreButton);
        header.setAlignment(Pos.CENTER_LEFT);

        similarSection = new VBox(6, header, firstSimilar, moreSimilar);
        similarSection.setPadding(new Insets(4, 16, 12, 16));
        setShown(similarSection, false);
        setShown(moreSimilar, false);

        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setScene(SceneUtil.create(new VBox(list, similarSection), 760, 720));
        stage.getScene().setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                stage.close();
            }
        });
        stage.setOnHidden(e -> {
            if (open == this) {
                open = null;
            }
        });
    }

    public static void show(Window owner, PostApiService.PostItem post, Consumer<Long> onOpenProfile) {
        show(owner, post, onOpenProfile, updated -> { });
    }

    /**
     * @param onOpenProfile клік по автору — вікно закривається й відкривається профіль
     * @param onChanged     лайк, збереження чи новий коментар у вікні — щоб оновити картку в стрічці
     */
    public static void show(Window owner, PostApiService.PostItem post,
                            Consumer<Long> onOpenProfile, Consumer<PostApiService.PostItem> onChanged) {
        if (open == null) {
            open = new PostViewDialog(owner);
        }
        open.display(post, onOpenProfile, onChanged);
    }

    /** Показати пост у цьому вікні (нове вікно не відкривається). */
    private void display(PostApiService.PostItem post, Consumer<Long> onOpenProfile,
                         Consumer<PostApiService.PostItem> onChanged) {
        this.onOpenProfile = onOpenProfile;
        this.onChanged = onChanged;
        replacing = true;
        list.getItems().setAll(post);
        replacing = false;
        list.scrollTo(0);
        stage.setTitle("Пост @" + post.authorUsername());

        firstSimilar.getChildren().clear();
        moreSimilar.getChildren().clear();
        setShown(similarSection, false);
        setShown(moreSimilar, false);
        loadSimilar(post.postId());

        if (stage.isShowing()) {
            stage.toFront();
        } else {
            stage.show();
        }
    }

    private void loadSimilar(Long postId) {
        int request = ++similarRequest;
        Thread.ofVirtual().start(() -> {
            try {
                PostApiService.FeedResult result = new PostApiService(new ApiClient()).getSimilarPosts(postId, SIMILAR);
                if (!result.success() || result.posts().isEmpty()) {
                    return;
                }
                Platform.runLater(() -> {
                    if (request == similarRequest) {
                        showSimilar(result.posts());
                    }
                });
            } catch (Exception ignored) {
                // схожі пости — додаткова інформація; без них вікно поста працює як і раніше
            }
        });
    }

    /** Перший схожий видно одразу, решта — під кнопкою «Ще». */
    private void showSimilar(List<PostApiService.PostItem> posts) {
        firstSimilar.getChildren().setAll(row(posts.get(0)));
        for (PostApiService.PostItem p : posts.subList(1, posts.size())) {
            moreSimilar.getChildren().add(row(p));
        }
        int rest = posts.size() - 1;
        moreButton.setText("Ще " + rest);
        setShown(moreButton, rest > 0);
        setShown(moreSimilar, false);
        setShown(similarSection, true);
    }

    private void toggleMore() {
        boolean expand = !moreSimilar.isVisible();
        setShown(moreSimilar, expand);
        moreButton.setText(expand ? "Згорнути" : "Ще " + moreSimilar.getChildren().size());
    }

    /** Рядок: автор, схожість і початок тексту; клік показує той пост у цьому ж вікні. */
    private VBox row(PostApiService.PostItem p) {
        String score = p.matchScore() == null ? "" : String.format(Locale.ROOT, " · схожість %.0f%%", p.matchScore() * 100);
        String tags = p.tags().isEmpty() ? "" : " · #" + String.join(" #", p.tags());
        Label meta = new Label(p.authorNameOrUsername() + " @" + p.authorUsername() + score + tags);
        meta.getStyleClass().add("post-handle");
        String text = p.text() == null ? "" : p.text().replace('\n', ' ');
        Label snippet = new Label(text.length() > SNIPPET ? text.substring(0, SNIPPET) + "…" : text);
        snippet.getStyleClass().add("post-text");
        VBox row = new VBox(2, meta, snippet);
        row.getStyleClass().add("similar-row");
        row.setOnMouseClicked(e -> display(p, onOpenProfile, onChanged));
        return row;
    }

    private static void setShown(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}