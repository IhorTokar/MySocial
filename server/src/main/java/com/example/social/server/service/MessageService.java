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
    public MessageDto sendMessage(Long senderId, Long receiverId, String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Message is empty");
        }
        String trimmed = text.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Message is too long (max " + MAX_LENGTH + ")");
        }
        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("You cannot message yourself");
        }

        User sender = findUser(senderId);
        User receiver = findUser(receiverId);

        Message message = new Message();
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setMessage(trimmed);

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
                            last.getMessage(),
                            last.getCreatedAt(),
                            unreadByPartner.getOrDefault(e.getKey(), 0L));
                })
                .collect(Collectors.toList());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private MessageDto toDto(Message m) {
        return new MessageDto(
                m.getMessageId(),
                m.getSender().getUserId(),
                m.getReceiver().getUserId(),
                m.getMessage(),
                m.getCreatedAt(),
                Boolean.TRUE.equals(m.getIsRead()));
    }
}