package com.example.social.server.integration;

import com.example.social.server.entity.UserRole;
import com.example.social.server.exception.BadRequestException;
import com.example.social.server.exception.ForbiddenException;
import com.example.social.server.service.MessageService;
import com.example.social.shared.dto.ConversationDto;
import com.example.social.shared.dto.MessageDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Особисті повідомлення: надсилання, відповіді, редагування, видалення, список діалогів і непрочитані. */
class MessageServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MessageService messages;

    @Test
    void conversationShowsBothSidesInOrderWithReplyQuote() {
        TestUser olena = createUser(UserRole.user);
        TestUser taras = createUser(UserRole.user);

        MessageDto question = messages.sendMessage(olena.id(), taras.id(), "  Як справи?  ", null, null);
        MessageDto answer = messages.sendMessage(taras.id(), olena.id(), "Добре!", null, question.getMessageId());

        assertEquals("Як справи?", question.getText(), "пробіли на краях обрізаються");

        List<MessageDto> dialog = messages.getConversation(olena.id(), taras.id());
        assertEquals(List.of(question.getMessageId(), answer.getMessageId()),
                dialog.stream().map(MessageDto::getMessageId).toList());
        assertEquals(dialog.stream().map(MessageDto::getMessageId).toList(),
                messages.getConversation(taras.id(), olena.id()).stream().map(MessageDto::getMessageId).toList(),
                "діалог однаковий з обох боків");

        MessageDto reply = dialog.get(1);
        assertEquals(question.getMessageId(), reply.getParentMessageId());
        assertEquals("Як справи?", reply.getParentSnippet());
        assertEquals(olena.username(), reply.getParentSenderUsername());
    }

    @Test
    void invalidMessagesAreRejected() {
        TestUser a = createUser(UserRole.user);
        TestUser b = createUser(UserRole.user);

        assertThrows(IllegalArgumentException.class, () -> messages.sendMessage(a.id(), a.id(), "собі", null, null));
        assertThrows(IllegalArgumentException.class, () -> messages.sendMessage(a.id(), b.id(), "   ", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> messages.sendMessage(a.id(), b.id(), "я".repeat(2001), null, null));
        // фото — лише з цього сервера, не довільна адреса
        assertThrows(BadRequestException.class,
                () -> messages.sendMessage(a.id(), b.id(), null, "https://example.com/cat.png", null));

        assertEquals(2000, messages.sendMessage(a.id(), b.id(), "я".repeat(2000), null, null).getText().length());
    }

    @Test
    void cannotReplyToMessageFromAnotherConversation() {
        TestUser a = createUser(UserRole.user);
        TestUser b = createUser(UserRole.user);
        TestUser c = createUser(UserRole.user);
        MessageDto toB = messages.sendMessage(a.id(), b.id(), "лише для B", null, null);

        assertThrows(IllegalArgumentException.class,
                () -> messages.sendMessage(a.id(), c.id(), "цитую чуже", null, toB.getMessageId()));
        assertThrows(IllegalArgumentException.class,
                () -> messages.sendMessage(c.id(), a.id(), "і я", null, toB.getMessageId()));
    }

    @Test
    void onlySenderCanEditOrDelete() {
        TestUser author = createUser(UserRole.user);
        TestUser reader = createUser(UserRole.user);
        MessageDto sent = messages.sendMessage(author.id(), reader.id(), "чернетка", null, null);

        assertThrows(ForbiddenException.class, () -> messages.editMessage(sent.getMessageId(), reader.id(), "підміна"));
        assertThrows(ForbiddenException.class, () -> messages.deleteMessage(sent.getMessageId(), reader.id()));

        MessageDto edited = messages.editMessage(sent.getMessageId(), author.id(), " остаточно ");
        assertEquals("остаточно", edited.getText());
        assertTrue(edited.isEdited());
    }

    @Test
    void deletedMessageLosesContentAndCannotBeEdited() {
        TestUser a = createUser(UserRole.user);
        TestUser b = createUser(UserRole.user);
        MessageDto original = messages.sendMessage(a.id(), b.id(), "секрет", null, null);
        messages.sendMessage(b.id(), a.id(), "що-що?", null, original.getMessageId());

        MessageDto deleted = messages.deleteMessage(original.getMessageId(), a.id());
        assertTrue(deleted.isDeleted());
        assertNull(deleted.getText(), "текст видаляється з бази, а не лише ховається");
        assertThrows(IllegalArgumentException.class, () -> messages.editMessage(original.getMessageId(), a.id(), "повернути"));

        MessageDto reply = messages.getConversation(a.id(), b.id()).get(1);
        assertEquals("Повідомлення видалено", reply.getParentSnippet(), "цитата не розкриває видалений текст");
    }

    @Test
    void conversationListShowsLastMessageAndUnreadUntilRead() {
        TestUser me = createUser(UserRole.user);
        TestUser friend = createUser(UserRole.user);
        TestUser other = createUser(UserRole.user);

        messages.sendMessage(friend.id(), me.id(), "перше", null, null);
        messages.sendMessage(me.id(), friend.id(), "моя відповідь", null, null);
        messages.sendMessage(friend.id(), me.id(), "друге", null, null);
        messages.sendMessage(friend.id(), me.id(), "третє", null, null);
        messages.sendMessage(other.id(), me.id(), "привіт від іншого", null, null);

        List<ConversationDto> list = messages.getConversations(me.id());
        assertEquals(List.of(other.id(), friend.id()), list.stream().map(ConversationDto::getUserId).toList(),
                "найсвіжіший діалог — першим");

        ConversationDto withFriend = list.get(1);
        assertEquals("третє", withFriend.getLastMessage());
        assertEquals(3, withFriend.getUnreadCount(), "власні повідомлення не рахуються непрочитаними");

        assertEquals(3, messages.markConversationRead(me.id(), friend.id()));
        ConversationDto afterRead = messages.getConversations(me.id()).get(1);
        assertEquals(0, afterRead.getUnreadCount());
        assertEquals(1, messages.getConversations(me.id()).get(0).getUnreadCount(), "інший діалог не зачеплено");

        assertFalse(messages.getConversation(friend.id(), me.id()).get(1).isRead(),
                "власне повідомлення співрозмовнику ще не прочитане ним");
    }
}