package com.example.social.server.repository;

import com.example.social.server.entity.Message;
import com.example.social.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findBySenderAndReceiverOrderByCreatedAtAsc(User sender, User receiver);
    long countByReceiverAndIsReadFalse(User receiver);
    @Query("SELECT m FROM Message m WHERE m.sender = :user OR m.receiver = :user")
    List<Message> findAllByUser(@Param("user") User user);

    // діалог в обидва боки; JOIN FETCH, щоб не було N+1 на sender/receiver
    @Query("SELECT m FROM Message m JOIN FETCH m.sender JOIN FETCH m.receiver " +
            "WHERE (m.sender = :a AND m.receiver = :b) OR (m.sender = :b AND m.receiver = :a) " +
            "ORDER BY m.createdAt ASC")
    List<Message> findConversation(@Param("a") User a, @Param("b") User b);

    // усі повідомлення користувача (для списку діалогів), найновіші першими
    @Query("SELECT m FROM Message m JOIN FETCH m.sender JOIN FETCH m.receiver " +
            "WHERE m.sender = :user OR m.receiver = :user ORDER BY m.createdAt DESC")
    List<Message> findAllInvolving(@Param("user") User user);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Message m SET m.isRead = true " +
            "WHERE m.receiver = :receiver AND m.sender = :sender AND m.isRead = false")
    int markAsRead(@Param("receiver") User receiver, @Param("sender") User sender);
}