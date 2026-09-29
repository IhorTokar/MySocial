package com.example.social.server.service;

import com.example.social.server.entity.Message;
import com.example.social.server.entity.User;
import com.example.social.server.repository.MessageRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.shared.dto.ConversationDto;
import com.example.social.shared.dto.MessageDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private static final int MAX_LENGTH = 2000;

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public MessageService(MessageRepository messageRepository, UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public MessageDto sendMessage(Long senderId, Long receiverId, String text, String photoUrl, Long parentMessageId) {
        boolean hasText = text != null && !text.isBlank();
        boolean hasPhoto = photoUrl != null && !photoUrl.isBlank();

        if (!hasText && !hasPhoto) {
            throw new IllegalArgumentException("Message must contain text or a photo");
        }
        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("You cannot message yourself");
        }

        String trimmed = null;
        if (hasText) {
            trimmed = text.trim();
            if (trimmed.length() > MAX_LENGTH) {
                throw new IllegalArgumentException("Message is too long (max " + MAX_LENGTH + ")");
            }
        }

        User sender = findUser(senderId);
        User receiver = findUser(receiverId);

        Message message = new Message();
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setMessage(trimmed);
        message.setMessageFileContent(hasPhoto ? photoUrl.trim() : null);

        if (parentMessageId != null) {
            Message parent = messageRepository.findById(parentMessageId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent message not found: " + parentMessageId));

            boolean sameConversation =
                    (parent.getSender().getUserId().equals(senderId) && parent.getReceiver().getUserId().equals(receiverId))
                            || (parent.getSender().getUserId().equals(receiverId) && parent.getReceiver().getUserId().equals(senderId));
            if (!sameConversation) {
                throw new IllegalArgumentException("Parent message is not part of this conversation");
            }
            message.setParentMessage(parent);
        }

        return toDto(messageRepository.save(message));
    }

    @Transactional
    public MessageDto editMessage(Long messageId, Long currentUserId, String newText) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        if (!message.getSender().getUserId().equals(currentUserId)) {
            throw new SecurityException("You can only edit your own messages");
        }
        if (Boolean.TRUE.equals(message.getDeleted())) {
            throw new IllegalArgumentException("Cannot edit a deleted message");
        }
        if (newText == null || newText.isBlank()) {
            throw new IllegalArgumentException("Message text cannot be empty");
        }

        String trimmed = newText.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Message is too long (max " + MAX_LENGTH + ")");
        }

        message.setMessage(trimmed);
        message.setEdited(true);
        return toDto(messageRepository.save(message));
    }

    @Transactional
    public MessageDto deleteMessage(Long messageId, Long currentUserId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        if (!message.getSender().getUserId().equals(currentUserId)) {
            throw new SecurityException("You can only delete your own messages");
        }

        message.setDeleted(true);
        message.setMessage(null);
        message.setMessageFileContent(null);
        return toDto(messageRepository.save(message));
    }

    @Transactional(readOnly = true)
    public List<MessageDto> getConversation(Long currentUserId, Long otherUserId) {
        User me = findUser(currentUserId);
        User other = findUser(otherUserId);
        return messageRepository.findConversation(me, other).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public int markConversationRead(Long currentUserId, Long otherUserId) {
        User me = findUser(currentUserId);
        User other = findUser(otherUserId);
        return messageRepository.markAsRead(me, other);
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> getConversations(Long currentUserId) {
        User me = findUser(currentUserId);
        List<Message> all = messageRepository.findAllInvolving(me); // найновіші першими

        Map<Long, Message> lastByPartner = new LinkedHashMap<>();
        Map<Long, User> partners = new HashMap<>();
        Map<Long, Long> unreadByPartner = new HashMap<>();

        for (Message m : all) {
            boolean iAmSender = m.getSender().getUserId().equals(currentUserId);
            User partner = iAmSender ? m.getReceiver() : m.getSender();
            Long partnerId = partner.getUserId();

            lastByPartner.putIfAbsent(partnerId, m);
            partners.putIfAbsent(partnerId, partner);

            if (!iAmSender && !Boolean.TRUE.equals(m.getIsRead())) {
                unreadByPartner.merge(partnerId, 1L, Long::sum);
            }
        }

        return lastByPartner.entrySet().stream()
                .map(e -> {
                    User partner = partners.get(e.getKey());
                    Message last = e.getValue();
                    return new ConversationDto(
                            partner.getUserId(),
                            partner.getUsername(),
                            partner.getDisplayName(),
                            lastMessagePreview(last),
                            last.getCreatedAt(),
                            unreadByPartner.getOrDefault(e.getKey(), 0L));
                })
                .collect(Collectors.toList());
    }

    private String lastMessagePreview(Message last) {
        if (Boolean.TRUE.equals(last.getDeleted())) {
            return "Повідомлення видалено";
        }
        if (last.getMessage() != null && !last.getMessage().isBlank()) {
            return last.getMessage();
        }
        if (last.getMessageFileContent() != null) {
            return "📷 Фото";
        }
        return "";
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    /**
     * N+1 попередження: для повідомлень-відповідей parentMessage і його sender
     * ліниво довантажуються окремим запитом на кожен елемент — прийнятно
     * для навчального проєкту, для продакшну варто додати JOIN FETCH-варіант.
     */
    private MessageDto toDto(Message m) {
        Long parentId = null;
        String parentSnippet = null;
        String parentSenderUsername = null;

        Message parent = m.getParentMessage();
        if (parent != null) {
            parentId = parent.getMessageId();
            parentSenderUsername = parent.getSender().getUsername();

            if (Boolean.TRUE.equals(parent.getDeleted())) {
                parentSnippet = "Повідомлення видалено";
            } else if (parent.getMessage() != null && !parent.getMessage().isBlank()) {
                String text = parent.getMessage();
                parentSnippet = text.length() > 60 ? text.substring(0, 60) + "…" : text;
            } else {
                parentSnippet = "Фото";
            }
        }

        return new MessageDto(
                m.getMessageId(),
                m.getSender().getUserId(),
                m.getReceiver().getUserId(),
                m.getMessage(),
                m.getMessageFileContent(),
                m.getCreatedAt(),
                Boolean.TRUE.equals(m.getIsRead()),
                Boolean.TRUE.equals(m.getEdited()),
                Boolean.TRUE.equals(m.getDeleted()),
                parentId, parentSnippet, parentSenderUsername
        );
    }
}