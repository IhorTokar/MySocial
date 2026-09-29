package com.example.social.client.service;

import com.example.social.client.util.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import java.util.concurrent.atomic.AtomicLong;

public class UnreadCounter {

    private static final UnreadCounter INSTANCE = new UnreadCounter();

    public static UnreadCounter getInstance() {
        return INSTANCE;
    }

    private final IntegerProperty count = new SimpleIntegerProperty(0);
    private final MessageApiService messageApiService = new MessageApiService(new ApiClient());
    private final AtomicLong requestSeq = new AtomicLong();

    private UnreadCounter() {
    }

    public ReadOnlyIntegerProperty countProperty() {
        return count;
    }

    /** Перераховує загальну кількість непрочитаних із сервера. Безпечно викликати з будь-якого потоку. */
    public void refresh() {
        if (!SessionManager.getInstance().isLoggedIn()) {
            return;
        }
        long seq = requestSeq.incrementAndGet();

        Thread.ofVirtual().start(() -> {
            try {
                MessageApiService.ConversationsResult result = messageApiService.getConversations();
                if (!result.success()) {
                    return;
                }
                int total = (int) result.conversations().stream()
                        .mapToLong(MessageApiService.ConversationItem::unreadCount)
                        .sum();

                Platform.runLater(() -> {
                    // застосовуємо лише результат найновішого запиту й лише якщо сесія ще жива
                    if (seq == requestSeq.get() && SessionManager.getInstance().isLoggedIn()) {
                        count.set(total);
                    }
                });
            } catch (Exception ignored) {
                // бейдж не критичний: лишаємо попереднє значення
            }
        });
    }

    public void reset() {
        requestSeq.incrementAndGet(); // скасовує результати запитів, що ще летять
        Platform.runLater(() -> count.set(0));
    }
}