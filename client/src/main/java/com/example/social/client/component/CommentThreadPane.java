package com.example.social.client.component;

import com.example.social.client.service.ApiClient;
import com.example.social.client.service.CommentApiService;
import com.example.social.client.util.AvatarUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongConsumer;

public class CommentThreadPane extends VBox {

    private static final int MAX_LENGTH = 1000;
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.of("uk"));

    private final CommentApiService commentApiService = new CommentApiService(new ApiClient());
    private final Long postId;
    private final LongConsumer onCommentCountChanged;

    private final VBox listBox = new VBox(10);
    private final Label replyBanner = new Label();
    private final TextField inputField = new TextField();
    private final Button sendButton = new Button("Надіслати");
    private final Label statusLabel = new Label();

    // розгорнуті гілки відповідей — переживають reload(), поки секція відкрита
    private final Set<Long> expandedThreads = new HashSet<>();

    private Long replyToId;      // кому реально відповідаємо (може бути відповіддю на відповідь)
    private Long replyThreadId;  // топ-коментар, під який покладеться нова відповідь
    private boolean loaded;

    public CommentThreadPane(Long postId, LongConsumer onCommentCountChanged) {
        super(10);
        this.postId = postId;
        this.onCommentCountChanged = onCommentCountChanged;
        build();
    }

    private void build() {
        getStyleClass().add("comment-thread");
        setPadding(new Insets(10, 0, 0, 0));

        replyBanner.getStyleClass().add("secondary-label");
        replyBanner.setVisible(false);
        replyBanner.setManaged(false);
        replyBanner.setCursor(javafx.scene.Cursor.HAND);

        inputField.setPromptText("Написати коментар...");
        inputField.setOnAction(e -> handleSend());
        HBox.setHgrow(inputField, Priority.ALWAYS);

        sendButton.getStyleClass().addAll("button", "button-secondary");
        sendButton.setOnAction(e -> handleSend());

        HBox inputRow = new HBox(8, inputField, sendButton);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        statusLabel.getStyleClass().add("error-label");

        getChildren().addAll(listBox, replyBanner, inputRow, statusLabel);
    }

    public void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        reload();
    }

    private void reload() {
        listBox.getChildren().setAll(new Label("Завантаження..."));

        Thread.ofVirtual().start(() -> {
            try {
                CommentApiService.CommentsResult result = commentApiService.getComments(postId);
                Platform.runLater(() -> {
                    if (result.success()) {
                        renderTree(result.comments());
                    } else {
                        listBox.getChildren().setAll(errorRow(result.errorMessage()));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> listBox.getChildren().setAll(errorRow("Помилка з'єднання")));
            }
        });
    }

    /**
     * Двошарова структура: топ-коментарі й усі їхні нащадки (незалежно від реальної
     * глибини parentCommentId) звалюються в один список відповідей під топ-коментарем.
     * Відповідь на відповідь технічно записується з parentCommentId відповіді,
     * але UI показує її на тому самому рівні — так простіше згортати/розгортати.
     */
    private void renderTree(List<CommentApiService.CommentItem> comments) {
        Map<Long, CommentApiService.CommentItem> byId = new HashMap<>();
        for (CommentApiService.CommentItem c : comments) {
            byId.put(c.commentId(), c);
        }

        List<CommentApiService.CommentItem> topLevel = new ArrayList<>();
        Map<Long, List<CommentApiService.CommentItem>> repliesByThreadRoot = new HashMap<>();

        for (CommentApiService.CommentItem c : comments) {
            if (c.parentCommentId() == null) {
                topLevel.add(c);
            } else {
                Long rootId = findThreadRoot(c, byId);
                repliesByThreadRoot.computeIfAbsent(rootId, k -> new ArrayList<>()).add(c);
            }
        }

        listBox.getChildren().clear();
        if (topLevel.isEmpty()) {
            Label empty = new Label("Коментарів ще немає");
            empty.getStyleClass().add("secondary-label");
            listBox.getChildren().add(empty);
            return;
        }

        for (CommentApiService.CommentItem c : topLevel) {
            List<CommentApiService.CommentItem> replies =
                    repliesByThreadRoot.getOrDefault(c.commentId(), List.of());
            listBox.getChildren().add(buildThreadNode(c, replies));
        }
    }

    /** Піднімається по ланцюжку parentCommentId, поки не знайде топ-коментар без батька. */
    private Long findThreadRoot(CommentApiService.CommentItem comment,
                                Map<Long, CommentApiService.CommentItem> byId) {
        CommentApiService.CommentItem current = comment;
        while (current.parentCommentId() != null) {
            CommentApiService.CommentItem parent = byId.get(current.parentCommentId());
            if (parent == null) {
                return current.parentCommentId(); // батько видалений, лишаємось на цьому рівні
            }
            current = parent;
        }
        return current.commentId();
    }

    private VBox buildThreadNode(CommentApiService.CommentItem root,
                                 List<CommentApiService.CommentItem> replies) {
        VBox threadBox = new VBox(6);

        threadBox.getChildren().add(buildCommentRow(root, root.commentId(), true));

        if (!replies.isEmpty()) {
            Button toggleButton = new Button();
            toggleButton.getStyleClass().add("link-button");

            VBox repliesBox = new VBox(8);
            repliesBox.setPadding(new Insets(0, 0, 0, 36));

            boolean expanded = expandedThreads.contains(root.commentId());
            repliesBox.setVisible(expanded);
            repliesBox.setManaged(expanded);
            toggleButton.setText(toggleLabel(expanded, replies.size()));

            for (CommentApiService.CommentItem reply : replies) {
                repliesBox.getChildren().add(buildCommentRow(reply, root.commentId(), false));
            }

            toggleButton.setOnAction(e -> {
                boolean nowExpanded = !repliesBox.isVisible();
                repliesBox.setVisible(nowExpanded);
                repliesBox.setManaged(nowExpanded);
                toggleButton.setText(toggleLabel(nowExpanded, replies.size()));
                if (nowExpanded) {
                    expandedThreads.add(root.commentId());
                } else {
                    expandedThreads.remove(root.commentId());
                }
            });

            HBox toggleRow = new HBox(toggleButton);
            toggleRow.setPadding(new Insets(0, 0, 0, 36));

            threadBox.getChildren().addAll(toggleRow, repliesBox);
        }

        return threadBox;
    }

    private String toggleLabel(boolean expanded, int count) {
        return expanded ? "Сховати відповіді" : "Показати відповіді (" + count + ")";
    }

    private VBox buildCommentRow(CommentApiService.CommentItem comment, Long threadRootId, boolean showReplyButton) {
        VBox holder = new VBox(6);

        var avatar = AvatarUtil.create(comment.authorNameOrUsername(), comment.authorAvatarUrl(), 28);

        Label nameLabel = new Label(comment.authorNameOrUsername());
        nameLabel.getStyleClass().add("post-author-name");

        Label timeLabel = new Label(formatDate(comment.createdAt()));
        timeLabel.getStyleClass().add("post-date");

        HBox header = new HBox(8, avatar, nameLabel, timeLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        Label textLabel = new Label(comment.text());
        textLabel.getStyleClass().add("post-text");
        textLabel.setWrapText(true);

        holder.getChildren().addAll(header, textLabel);

        if (showReplyButton) {
            Button replyButton = new Button("Відповісти");
            replyButton.getStyleClass().add("link-button");
            replyButton.setOnAction(e -> startReply(comment.commentId(), threadRootId, comment.authorNameOrUsername()));
            holder.getChildren().add(replyButton);
        }

        return holder;
    }

    private void startReply(Long commentId, Long threadRootId, String authorName) {
        replyToId = commentId;
        replyThreadId = threadRootId;
        replyBanner.setText("Відповідь для " + authorName + "  ✕");
        replyBanner.setVisible(true);
        replyBanner.setManaged(true);
        replyBanner.setOnMouseClicked(e -> cancelReply());
        inputField.requestFocus();
    }

    private void cancelReply() {
        replyToId = null;
        replyThreadId = null;
        replyBanner.setVisible(false);
        replyBanner.setManaged(false);
    }

    private void handleSend() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > MAX_LENGTH) {
            statusLabel.setText("Коментар задовгий (максимум " + MAX_LENGTH + ")");
            return;
        }

        sendButton.setDisable(true);
        statusLabel.setText("");
        Long parentId = replyToId;
        Long threadId = replyThreadId;

        Thread.ofVirtual().start(() -> {
            try {
                CommentApiService.ActionResult result = commentApiService.addComment(postId, text, parentId);

                Platform.runLater(() -> {
                    sendButton.setDisable(false);
                    if (result.success()) {
                        inputField.clear();
                        cancelReply();
                        if (threadId != null) {
                            expandedThreads.add(threadId); // щойно додана відповідь одразу видима
                        }
                        reload();
                        if (onCommentCountChanged != null) {
                            onCommentCountChanged.accept(1);
                        }
                    } else {
                        statusLabel.setText(result.errorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    sendButton.setDisable(false);
                    statusLabel.setText("Помилка з'єднання");
                });
            }
        });
    }

    private HBox errorRow(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("error-label");
        return new HBox(label);
    }

    private String formatDate(String iso) {
        try {
            return LocalDateTime.parse(iso).format(DISPLAY_FORMAT);
        } catch (Exception e) {
            return iso;
        }
    }
}