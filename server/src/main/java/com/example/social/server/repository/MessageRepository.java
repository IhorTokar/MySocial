package com.example.social.server.repository;

import com.example.social.server.entity.Message;
import com.example.social.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findBySenderAndReceiverOrderByCreatedAtAsc(User sender, User receiver);
    long countByReceiverAndIsReadFalse(User receiver);
}