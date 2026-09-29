package com.example.social.client.controller;

import com.example.social.client.service.ApiClient;
import com.example.social.client.service.ChatConnection;
import com.example.social.client.service.MessageApiService;
import com.example.social.client.service.UnreadCounter;
import com.example.social.client.util.SessionManager;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DialogsController {

    private static final int MAX_LENGTH = 2000;
    private static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;
    private static final String[] EMOJIS = {
            "😀", "😁", "😂", "🙂", "😉", "😍", "😢", "😡", "👍", "👎",
            "❤️", "🔥", "🎉", "😎", "🤔", "😴", "🙏", "👏", "💯", "✨"
    };

    @FXML private ListView<MessageApiService.ConversationItem> conversationsListView;
    @FXML private ListView<MessageApiService.MessageItem> messagesListView;
    @FXML private Label chatTitleLabel;
    @FXML private Label statusLabel;
    @FXML private Label contextBannerLabel;
    @FXML private HBox photoPreviewBox;
    @FXML private ImageView photoPreviewView;
    @FXML private FlowPane emojiPane;
    @FXML private Button emojiButton;
    @FXML private Button photoButton;
    @FXML private TextField messageField;
    @FXML private Button sendButton;

    private final MessageApiService messageApiService = new MessageApiService(new ApiClient());
    private final ChatConnection chat = ChatConnection.getInstance();
    private final Long myId = SessionManager.getInstance().getUserId();

    private Long activeUserId;
    private String activeTitle;
    private Long pendingOpenUserId;
    private String pendingOpenTitle;
    private boolean suppressSelectionEvents;

    private Long replyToId;
    private Long editingMessageId;
    private String pendingPhotoUrl;

    private ChatConnection.Listener chatListener;

    @FXML
    private void initialize() {
        conversationsListView.setCellFactory(list -> new ConversationCell());
        messagesListView.setCellFactory(list -> new MessageCell());
        setChatEnabled(false);
        buildEmojiPane();

        conversationsListView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (suppressSelectionEvents || selected == null) {
                return;
            }
            openConversation(selected.userId(), selected.displayNameOrUsername());
        });

        chatListener = new ChatConnection.Listener() {
            @Override
            public void onMessage(MessageApiService.MessageItem message) {
                if (messagesListView.getScene() == null) {
                    chat.removeListener(this);
                    return;
                }
                handleIncoming(message);
            }

            @Override
            public void onMessageEdited(MessageApiService.MessageItem message) {
                if (messagesListView.getScene() == null) {
                    chat.removeListener(this);
                    return;
                }
                replaceMessage(message);
                loadConversations();
            }

            @Override
            public void onMessageDeleted(MessageApiService.MessageItem message) {
                if (messagesListView.getScene() == null) {
                    chat.removeListener(this);
                    return;
                }
                replaceMessage(message);
                loadConversations();
            }

            @Override
            public void onError(String error) {
                if (messagesListView.getScene() == null) {
                    chat.removeListener(this);
                    return;
                }
                showError(error);
            }
        };
        chat.addListener(chatListener);

        loadConversations();
    }

    public void openWith(Long userId, String title) {
        pendingOpenUserId = userId;
        pendingOpenTitle = title;
        loadConversations();
    }

    // ===== Завантаження =====

    private void loadConversations() {
        Thread.ofVirtual().start(() -> {
            try {
                MessageApiService.ConversationsResult result = messageApiService.getConversations();
                Platform.runLater(() -> {
                    if (result.success()) {
                        applyConversations(result.conversations());
                    } else {
                        showError(result.errorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Помилка завантаження діалогів: " + e.getMessage()));
            }
        });
    }

    private void applyConversations(List<MessageApiService.ConversationItem> items) {
        List<MessageApiService.ConversationItem> list = new ArrayList<>(items);

        Long toOpen = null;
        String toOpenTitle = null;
        if (pendingOpenUserId != null) {
            toOpen = pendingOpenUserId;
            toOpenTitle = pendingOpenTitle;
            pendingOpenUserId = null;
            pendingOpenTitle = null;
            ensurePresent(list, toOpen, toOpenTitle);
        } else if (activeUserId != null) {
            ensurePresent(list, activeUserId, activeTitle);
        }

        Long selectId = toOpen != null ? toOpen : activeUserId;

        suppressSelectionEvents = true;
        conversationsListView.getItems().setAll(list);
        if (selectId != null) {
            selectConversation(selectId);
        }
        suppressSelectionEvents = false;

        if (toOpen != null) {
            openConversation(toOpen, toOpenTitle);
        }
    }

    private void ensurePresent(List<MessageApiService.ConversationItem> list, Long userId, String title) {
        boolean exists = list.stream().anyMatch(c -> c.userId().equals(userId));
        if (!exists) {
            list.add(0, new MessageApiService.ConversationItem(userId, title, title, "", null, 0));
        }
    }

    private void selectConversation(Long userId) {
        List<MessageApiService.ConversationItem> items = conversationsListView.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).userId().equals(userId)) {
                conversationsListView.getSelectionModel().select(i);
                return;
            }
        }
    }

    private void openConversation(Long userId, String title) {
        activeUserId = userId;
        activeTitle = title;
        chatTitleLabel.setText(title);
        statusLabel.setText("");
        setChatEnabled(true);
        messagesListView.getItems().clear();
        clearBannerState();
        clearPhotoPreview();

        Thread.ofVirtual().start(() -> {
            try {
                MessageApiService.MessagesResult result = messageApiService.getMessagesWith(userId);
                Platform.runLater(() -> {
                    if (!userId.equals(activeUserId)) {
                        return;
                    }
                    if (result.success()) {
                        messagesListView.getItems().setAll(result.messages());
                        scrollToBottom();
                        markReadAndRefresh(userId);
                    } else {
                        showError(result.errorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Помилка завантаження повідомлень: " + e.getMessage()));
            }
        });
    }

    private void markReadAndRefresh(Long otherUserId) {
        Thread.ofVirtual().start(() -> {
            try {
                messageApiService.markRead(otherUserId);
            } catch (Exception ignored) {
                // не критично
            }
            UnreadCounter.getInstance().refresh();
            Platform.runLater(this::loadConversations);
        });
    }

    // ===== Реальний час =====

    private void handleIncoming(MessageApiService.MessageItem message) {
        boolean fromMe = message.senderId().equals(myId);
        Long partnerId = fromMe ? message.receiverId() : message.senderId();

        if (partnerId.equals(activeUserId)) {
            messagesListView.getItems().add(message);
            scrollToBottom();
            if (!fromMe) {
                markReadAndRefresh(partnerId);
                return;
            }
        }
        loadConversations();
    }

    private void replaceMessage(MessageApiService.MessageItem updated) {
        ObservableList<MessageApiService.MessageItem> items = messagesListView.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).messageId().equals(updated.messageId())) {
                items.set(i, updated);
                return;
            }
        }
    }

    // ===== Емодзі =====

    private void buildEmojiPane() {
        for (String emoji : EMOJIS) {
            Button b = new Button(emoji);
            b.getStyleClass().add("action-button");
            b.setOnAction(e -> insertEmoji(emoji));
            emojiPane.getChildren().add(b);
        }
    }

    private void insertEmoji(String emoji) {
        int caret = messageField.getCaretPosition();
        messageField.insertText(caret, emoji);
        messageField.requestFocus();
        messageField.positionCaret(caret + emoji.length());
    }

    @FXML
    private void handleToggleEmoji() {
        boolean show = !emojiPane.isVisible();
        emojiPane.setVisible(show);
        emojiPane.setManaged(show);
    }

    // ===== Фото =====

    @FXML
    private void handleChoosePhoto() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Оберіть фото");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Зображення", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));

        File file = chooser.showOpenDialog(photoButton.getScene().getWindow());
        if (file == null) {
            return;
        }
        if (file.length() > MAX_PHOTO_BYTES) {
            showError("Файл завеликий (максимум 10 МБ)");
            return;
        }

        photoButton.setDisable(true);
        statusLabel.getStyleClass().setAll("secondary-label");
        statusLabel.setText("Завантаження фото...");

        Thread.ofVirtual().start(() -> {
            try {
                MessageApiService.UploadResult result = messageApiService.uploadPhoto(file.toPath());
                Platform.runLater(() -> {
                    photoButton.setDisable(false);
                    if (result.success()) {
                        pendingPhotoUrl = result.url();
                        photoPreviewView.setImage(new Image(file.toURI().toString(), 0, 80, true, true, true));
                        photoPreviewBox.setVisible(true);
                        photoPreviewBox.setManaged(true);
                        statusLabel.setText("");
                    } else {
                        showError(result.errorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    photoButton.setDisable(false);
                    showError("Помилка завантаження фото: " + e.getMessage());
                });
            }
        });
    }

    @FXML
    private void handleRemovePhoto() {
        clearPhotoPreview();
    }

    private void clearPhotoPreview() {
        pendingPhotoUrl = null;
        photoPreviewView.setImage(null);
        photoPreviewBox.setVisible(false);
        photoPreviewBox.setManaged(false);
    }

    // ===== Контекст (відповідь / редагування) =====

    private void startReply(Long messageId, String snippet, String authorName) {
        clearBannerState();
        replyToId = messageId;
        contextBannerLabel.setText("Відповідь для " + authorName + ": " + snippet + "  ✕");
        contextBannerLabel.setVisible(true);
        contextBannerLabel.setManaged(true);
        messageField.requestFocus();
    }

    private void beginMessageEdit(MessageApiService.MessageItem item) {
        clearBannerState();
        editingMessageId = item.messageId();
        messageField.setText(item.text() != null ? item.text() : "");
        contextBannerLabel.setText("Редагування повідомлення  ✕");
        contextBannerLabel.setVisible(true);
        contextBannerLabel.setManaged(true);
        sendButton.setText("Зберегти");
        photoButton.setDisable(true);
        messageField.requestFocus();
    }

    @FXML
    private void handleCancelContext() {
        clearBannerState();
        messageField.clear();
    }

    private void clearBannerState() {
        replyToId = null;
        editingMessageId = null;
        contextBannerLabel.setVisible(false);
        contextBannerLabel.setManaged(false);
        sendButton.setText("Надіслати");
        photoButton.setDisable(false);
    }

    // ===== Надсилання / редагування / видалення =====

    @FXML
    private void handleSend() {
        if (activeUserId == null) {
            return;
        }

        if (editingMessageId != null) {
            String text = messageField.getText().trim();
            if (text.isEmpty()) {
                showError("Текст не може бути порожнім");
                return;
            }
            if (text.length() > MAX_LENGTH) {
                showError("Повідомлення задовге (максимум " + MAX_LENGTH + " символів)");
                return;
            }

            if (chat.editMessage(editingMessageId, text)) {
                clearBannerState();
                messageField.clear();
                statusLabel.setText("");
            } else {
                showError("Немає з'єднання з чатом, пробую перепідключитись...");
                chat.connect();
            }
            return;
        }

        String text = messageField.getText().trim();
        if (text.isEmpty() && pendingPhotoUrl == null) {
            return;
        }
        if (text.length() > MAX_LENGTH) {
            showError("Повідомлення задовге (максимум " + MAX_LENGTH + " символів)");
            return;
        }

        String textToSend = text.isEmpty() ? null : text;
        Long parentId = replyToId;

        if (chat.sendMessage(activeUserId, textToSend, pendingPhotoUrl, parentId)) {
            messageField.clear();
            clearPhotoPreview();
            clearBannerState();
            statusLabel.setText("");
        } else {
            showError("Немає з'єднання з чатом, пробую перепідключитись...");
            chat.connect();
        }
    }

    private void handleDeleteMessage(Long messageId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Видалити це повідомлення?");
        confirm.setHeaderText(null);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        if (!chat.deleteMessage(messageId)) {
            showError("Немає з'єднання з чатом, пробую перепідключитись...");
            chat.connect();
        }
    }

    // ===== Допоміжне =====

    private void setChatEnabled(boolean enabled) {
        messageField.setDisable(!enabled);
        sendButton.setDisable(!enabled);
        emojiButton.setDisable(!enabled);
        photoButton.setDisable(!enabled);
    }

    private void showError(String message) {
        statusLabel.getStyleClass().setAll("error-label");
        statusLabel.setText(message);
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            int size = messagesListView.getItems().size();
            if (size > 0) {
                messagesListView.scrollTo(size - 1);
            }
        });
    }

    private static String formatTime(String iso) {
        try {
            return LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("dd.MM HH:mm"));
        } catch (Exception e) {
            return "";
        }
    }

    private static ImageView buildPhotoView(String url) {
        ImageView iv = new ImageView();
        String fullUrl = url.startsWith("http") ? url : ApiClient.BASE_URL + url;
        try {
            iv.setImage(new Image(fullUrl, 240, 0, true, true, true));
        } catch (Exception ignored) {
            // лишаємо порожнє зображення, якщо не вдалось завантажити
        }
        iv.setFitWidth(240);
        iv.setPreserveRatio(true);
        Rectangle clip = new Rectangle();
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        iv.layoutBoundsProperty().addListener((obs, old, bounds) -> {
            clip.setWidth(bounds.getWidth());
            clip.setHeight(bounds.getHeight());
        });
        iv.setClip(clip);
        return iv;
    }

    // ===== Клітинки =====

    private static class ConversationCell extends ListCell<MessageApiService.ConversationItem> {
        private final Label nameLabel = new Label();
        private final Label previewLabel = new Label();
        private final Label unreadLabel = new Label();
        private final Region spacer = new Region();
        private final HBox topRow = new HBox(8, nameLabel, spacer, unreadLabel);
        private final VBox box = new VBox(2, topRow, previewLabel);

        ConversationCell() {
            nameLabel.getStyleClass().add("post-author-name");
            previewLabel.getStyleClass().add("secondary-label");
            unreadLabel.getStyleClass().add("unread-badge");
            box.getStyleClass().add("conversation-box");
            HBox.setHgrow(spacer, Priority.ALWAYS);
            topRow.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(MessageApiService.ConversationItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            nameLabel.setText(item.displayNameOrUsername());

            String preview = item.lastMessage() == null ? "" : item.lastMessage();
            previewLabel.setText(preview.length() > 45 ? preview.substring(0, 45) + "…" : preview);

            boolean hasUnread = item.unreadCount() > 0;
            unreadLabel.setText(String.valueOf(item.unreadCount()));
            unreadLabel.setVisible(hasUnread);
            unreadLabel.setManaged(hasUnread);

            setGraphic(box);
        }

        @Override
        public void updateSelected(boolean selected) {
            super.updateSelected(selected);
            box.getStyleClass().remove("conversation-active");
            if (selected) {
                box.getStyleClass().add("conversation-active");
            }
        }
    }

    private class MessageCell extends ListCell<MessageApiService.MessageItem> {

        MessageCell() {
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(MessageApiService.MessageItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            boolean mine = item.senderId().equals(myId);
            VBox column = new VBox(4);
            column.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

            if (item.parentMessageId() != null) {
                String author = item.parentSenderUsername() != null ? item.parentSenderUsername() + ": " : "";
                Label quote = new Label("↩ " + author + item.parentSnippet());
                quote.getStyleClass().add("message-quote");
                quote.setWrapText(true);
                quote.setMaxWidth(380);
                column.getChildren().add(quote);
            }

            if (item.deleted()) {
                Label deletedLabel = new Label("Повідомлення видалено");
                deletedLabel.getStyleClass().add("secondary-label");
                deletedLabel.setStyle("-fx-font-style: italic;");
                column.getChildren().add(deletedLabel);
            } else {
                VBox bubble = new VBox(6);
                bubble.getStyleClass().add(mine ? "bubble-mine" : "bubble-theirs");
                bubble.setMaxWidth(380);

                if (item.text() != null && !item.text().isBlank()) {
                    Label textLabel = new Label(item.text());
                    textLabel.setWrapText(true);
                    textLabel.getStyleClass().add(mine ? "bubble-text-mine" : "bubble-text-theirs");
                    bubble.getChildren().add(textLabel);
                }
                if (item.photoUrl() != null && !item.photoUrl().isBlank()) {
                    bubble.getChildren().add(buildPhotoView(item.photoUrl()));
                }

                column.getChildren().add(bubble);
            }

            Label time = new Label(formatTime(item.createdAt()) + (item.edited() ? " · змінено" : ""));
            time.getStyleClass().add("secondary-label");
            HBox metaRow = new HBox(time);
            metaRow.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            column.getChildren().add(metaRow);

            if (!item.deleted()) {
                HBox actionsRow = new HBox(10);
                actionsRow.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

                Button replyButton = new Button("Відповісти");
                replyButton.getStyleClass().add("link-button");
                replyButton.setOnAction(e -> {
                    String base = item.text() != null ? item.text() : "Фото";
                    String preview = base.length() > 60 ? base.substring(0, 60) + "…" : base;
                    String authorName = mine ? "себе" : chatTitleLabel.getText();
                    startReply(item.messageId(), preview, authorName);
                });
                actionsRow.getChildren().add(replyButton);

                if (mine) {
                    Button editButton = new Button("Редагувати");
                    editButton.getStyleClass().add("link-button");
                    editButton.setOnAction(e -> beginMessageEdit(item));
                    actionsRow.getChildren().add(editButton);

                    Button deleteButton = new Button("Видалити");
                    deleteButton.getStyleClass().add("link-button");
                    deleteButton.setOnAction(e -> handleDeleteMessage(item.messageId()));
                    actionsRow.getChildren().add(deleteButton);
                }

                column.getChildren().add(actionsRow);
            }

            setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            setGraphic(column);
        }
    }
}