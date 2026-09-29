package com.example.social.client.service;

import com.example.social.client.util.SessionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class ChatConnection {

    private static final Logger LOG = Logger.getLogger(ChatConnection.class.getName());
    private static final ChatConnection INSTANCE = new ChatConnection();
    private static final int MAX_CONSECUTIVE_FAILURES = 10;
    private static final long RECONNECT_DELAY_MS = 3000;

    public static ChatConnection getInstance() {
        return INSTANCE;
    }

    /** Колбеки викликаються в JavaFX-потоці. */
    public interface Listener {
        default void onMessage(MessageApiService.MessageItem message) {}
        default void onError(String error) {}
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicInteger failures = new AtomicInteger();

    private volatile WebSocketClient client;
    private volatile boolean shouldRun;

    private ChatConnection() {
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public synchronized void connect() {
        if (!SessionManager.getInstance().isLoggedIn()) {
            return;
        }
        shouldRun = true;
        failures.set(0);

        WebSocketClient current = client;
        if (current != null) {
            ReadyState state = current.getReadyState();
            if (state == ReadyState.OPEN || state == ReadyState.NOT_YET_CONNECTED) {
                return; // вже з'єднані або з'єднуємось
            }
        }
        openNewClient();
    }

    public synchronized void disconnect() {
        shouldRun = false;
        WebSocketClient current = client;
        client = null;
        if (current != null) {
            current.close();
        }
    }

    public boolean isConnected() {
        WebSocketClient current = client;
        return current != null && current.isOpen();
    }

    public boolean send(Long toUserId, String text) {
        WebSocketClient current = client;
        if (current == null || !current.isOpen()) {
            return false;
        }
        try {
            current.send(mapper.writeValueAsString(Map.of("toUserId", toUserId, "text", text)));
            return true;
        } catch (Exception e) {
            LOG.warning("Failed to send chat message: " + e.getMessage());
            return false;
        }
    }

    private void openNewClient() {
        String token = SessionManager.getInstance().getToken();
        if (token == null) {
            return;
        }

        URI uri = URI.create(ApiClient.BASE_URL.replaceFirst("^http", "ws") + "/ws/chat");
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);

        WebSocketClient created = new WebSocketClient(uri, headers) {
            private boolean opened = false;

            @Override
            public void onOpen(ServerHandshake handshake) {
                opened = true;
                failures.set(0);
            }

            @Override
            public void onMessage(String message) {
                handleIncoming(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                if (!opened) {
                    failures.incrementAndGet();
                }
                scheduleReconnect(this);
            }

            @Override
            public void onError(Exception ex) {
                LOG.warning("Chat WebSocket error: " + ex.getMessage());
            }
        };

        created.setConnectionLostTimeout(30); // ping/pong, щоб бачити обрив
        client = created;
        created.connect(); // неблокуюче
    }

    private void scheduleReconnect(WebSocketClient closed) {
        if (!shouldRun || closed != client || failures.get() >= MAX_CONSECUTIVE_FAILURES) {
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(RECONNECT_DELAY_MS);
            } catch (InterruptedException e) {
                return;
            }
            synchronized (ChatConnection.this) {
                if (shouldRun && client == closed) {
                    openNewClient();
                }
            }
        });
    }

    private void handleIncoming(String raw) {
        try {
            JsonNode node = mapper.readTree(raw);
            String type = node.path("type").asText();

            if ("message".equals(type)) {
                MessageApiService.MessageItem item = MessageApiService.parseMessage(node.get("message"));
                Platform.runLater(() -> listeners.forEach(l -> l.onMessage(item)));
            } else if ("error".equals(type)) {
                String error = node.path("error").asText("Помилка чату");
                Platform.runLater(() -> listeners.forEach(l -> l.onError(error)));
            }
        } catch (Exception e) {
            LOG.warning("Failed to parse chat payload: " + e.getMessage());
        }
    }
}