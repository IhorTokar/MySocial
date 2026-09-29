package com.example.social.server.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
public class ChatSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionRegistry.class);

    // один користувач може мати кілька відкритих вікон/сесій
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(Long userId, WebSocketSession session) {
        sessions.computeIfPresent(userId, (k, set) -> {
            set.remove(session);
            return set.isEmpty() ? null : set;
        });
    }

    public void sendToUser(Long userId, String payload) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null) {
            return; // користувач офлайн: побачить повідомлення з історії при відкритті діалогу
        }
        for (WebSocketSession session : userSessions) {
            send(session, payload);
        }
    }

    public void send(WebSocketSession session, String payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            // WebSocketSession.sendMessage не потокобезпечний
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException e) {
            log.warn("Failed to send WebSocket message: {}", e.getMessage());
        }
    }
}