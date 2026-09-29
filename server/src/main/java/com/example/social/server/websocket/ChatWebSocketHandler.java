package com.example.social.server.websocket;

import com.example.social.server.service.MessageService;
import com.example.social.shared.dto.MessageDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final MessageService messageService;
    private final ChatSessionRegistry registry;
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler(MessageService messageService,
                                ChatSessionRegistry registry,
                                ObjectMapper objectMapper) {
        this.messageService = messageService;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = userIdOf(session);
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        registry.register(userId, session);
        log.info("Chat connected: user={} session={}", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long senderId = userIdOf(session);
        if (senderId == null) {
            return;
        }

        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String type = node.path("type").asText("send");

            switch (type) {
                case "send" -> handleSend(senderId, node);
                case "edit" -> handleEdit(senderId, node);
                case "delete" -> handleDelete(senderId, node);
                default -> sendError(session, "Unknown message type: " + type);
            }
        } catch (IllegalArgumentException | SecurityException e) {
            sendError(session, e.getMessage());
        } catch (Exception e) {
            log.warn("Bad chat message from user {}: {}", senderId, e.getMessage());
            sendError(session, "Некоректне повідомлення");
        }
    }

    private void handleSend(Long senderId, JsonNode node) throws Exception {
        if (!node.hasNonNull("toUserId")) {
            throw new IllegalArgumentException("toUserId is required");
        }
        Long toUserId = node.get("toUserId").asLong();
        String text = node.hasNonNull("text") ? node.get("text").asText() : null;
        String photoUrl = node.hasNonNull("photoUrl") ? node.get("photoUrl").asText() : null;
        Long parentMessageId = node.hasNonNull("parentMessageId") ? node.get("parentMessageId").asLong() : null;

        MessageDto saved = messageService.sendMessage(senderId, toUserId, text, photoUrl, parentMessageId);
        broadcast("message", saved);
    }

    private void handleEdit(Long senderId, JsonNode node) throws Exception {
        if (!node.hasNonNull("messageId") || !node.hasNonNull("text")) {
            throw new IllegalArgumentException("messageId and text are required");
        }
        Long messageId = node.get("messageId").asLong();
        String text = node.get("text").asText();

        MessageDto updated = messageService.editMessage(messageId, senderId, text);
        broadcast("message_edited", updated);
    }

    private void handleDelete(Long senderId, JsonNode node) throws Exception {
        if (!node.hasNonNull("messageId")) {
            throw new IllegalArgumentException("messageId is required");
        }
        Long messageId = node.get("messageId").asLong();

        MessageDto updated = messageService.deleteMessage(messageId, senderId);
        broadcast("message_deleted", updated);
    }

    private void broadcast(String type, MessageDto dto) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of("type", type, "message", dto));
        registry.sendToUser(dto.getSenderId(), payload);
        registry.sendToUser(dto.getReceiverId(), payload);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userIdOf(session);
        if (userId != null) {
            registry.unregister(userId, session);
            log.info("Chat disconnected: user={} session={}", userId, session.getId());
        }
    }

    private void sendError(WebSocketSession session, String error) {
        try {
            registry.send(session, objectMapper.writeValueAsString(Map.of("type", "error", "error", error)));
        } catch (Exception ignored) {
            // нічого критичного
        }
    }

    private Long userIdOf(WebSocketSession session) {
        Object value = session.getAttributes().get("userId");
        return value instanceof Long id ? id : null;
    }
}