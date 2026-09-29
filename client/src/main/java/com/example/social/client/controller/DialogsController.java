package com.example.social.client.controller;

import com.example.social.client.service.ApiClient;
import com.example.social.client.service.ChatConnection;
import com.example.social.client.service.MessageApiService;
import com.example.social.client.service.UnreadCounter;
import com.example.social.client.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class DialogsController {

    private static final int MAX_LENGTH = 2000;

    @FXML private ListView<MessageApiService.ConversationItem> conversationsListView;
    @FXML private ListView<MessageApiService.MessageItem> messagesListView;
    @FXML private Label chatTitleLabel;
    @FXML private Label statusLabel;
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

    private ChatConnection.Listener chatListener;

    @FXML
    private void initialize() {
        conversationsListView.setCellFactory(list -> new ConversationCell());
        messagesListView.setCellFactory(list -> new MessageCell());
        setChatEnabled(false);

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
                    chat.removeListener(this); // екран уже замінено іншим
                    return;
                }
                handleIncoming(message);
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

    /** Відкрити діалог з конкретним користувачем (наприклад, з кнопки «Написати» в профілі). */
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
            ensurePresent(list, activeUserId, activeTitle); // діалог без історії не зникає при оновленні
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

        Thread.ofVirtual().start(() -> {
            try {
                MessageApiService.MessagesResult result = messageApiService.getMessagesWith(userId);
                Platform.runLater(() -> {
                    if (!userId.equals(activeUserId)) {
                        return; // користувач уже переключився на інший діалог
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

    // ===== Надсилання =====

    @FXML
    private void handleSend() {
        if (activeUserId == null) {
            return;
        }
        String text = messageField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > MAX_LENGTH) {
            showError("Повідомлення задовге (максимум " + MAX_LENGTH + " символів)");
            return;
        }

        if (chat.send(activeUserId, text)) {
            messageField.clear();
            statusLabel.setText("");
        } else {
            showError("Немає з'єднання з чатом, пробую перепідключитись...");
            chat.connect();
        }
    }

    // ===== Допоміжне =====

    private void setChatEnabled(boolean enabled) {
        messageField.setDisable(!enabled);
        sendButton.setDisable(!enabled);
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
            setPrefWidth(0); // щоб довгий текст переносився, а не давав горизонтальний скрол
        }

        @Override
        protected void updateItem(MessageApiService.MessageItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            boolean mine = item.senderId().equals(myId);

            Label bubble = new Label(item.text());
            bubble.setWrapText(true);
            bubble.setMaxWidth(380);
            bubble.getStyleClass().add(mine ? "bubble-mine" : "bubble-theirs");

            Label time = new Label(formatTime(item.createdAt()));
            time.getStyleClass().add("secondary-label");

            VBox column = new VBox(2, bubble, time);
            column.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

            setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            setGraphic(column);
        }
    }
}