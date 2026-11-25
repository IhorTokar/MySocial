package com.example.social.server.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String IP = "10.0.0.1";

    /** Годинник, який тест пересуває вручну. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-11-25T12:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final TestClock clock = new TestClock();
    private final LoginAttemptService attempts = new LoginAttemptService(5, 30, WINDOW, clock);

    private void fail(String username, String ip, int times) {
        for (int i = 0; i < times; i++) {
            attempts.recordFailure(username, ip);
        }
    }

    @Test
    void fourFailuresAreAllowedFifthBlocks() {
        fail("olena", IP, 4);
        assertTrue(attempts.blockedFor("olena", IP).isEmpty());

        fail("olena", IP, 1);
        assertEquals(WINDOW, attempts.blockedFor("olena", IP).orElseThrow());
    }

    @Test
    void blockLastsUntilEndOfWindowCountedFromFirstFailure() {
        fail("olena", IP, 1);
        clock.advance(Duration.ofMinutes(5));
        fail("olena", IP, 4);
        assertEquals(Duration.ofMinutes(10), attempts.blockedFor("olena", IP).orElseThrow());

        clock.advance(Duration.ofMinutes(10));
        assertTrue(attempts.blockedFor("olena", IP).isEmpty());
    }

    @Test
    void newWindowStartsFromScratchAfterExpiry() {
        fail("olena", IP, 4);
        clock.advance(WINDOW);
        fail("olena", IP, 4);
        assertTrue(attempts.blockedFor("olena", IP).isEmpty(), "старі невдачі не мають сумуватися з новими");
    }

    @Test
    void successResetsUserCounter() {
        fail("olena", IP, 4);
        attempts.recordSuccess("olena");
        fail("olena", IP, 4);
        assertTrue(attempts.blockedFor("olena", IP).isEmpty());
    }

    @Test
    void usernameIsCaseAndSpaceInsensitive() {
        fail("Olena", IP, 3);
        fail(" olena ", IP, 2);
        assertTrue(attempts.blockedFor("OLENA", IP).isPresent());
    }

    @Test
    void otherUsersAreNotAffected() {
        fail("olena", IP, 5);
        assertTrue(attempts.blockedFor("taras", "10.0.0.2").isEmpty());
    }

    @Test
    void ipLimitBlocksSprayingAcrossManyUsernames() {
        // по 3 спроби на 10 різних логінів — жоден логін не досяг 5, але з адреси вже 30
        for (int u = 0; u < 10; u++) {
            fail("user" + u, IP, 3);
        }
        assertTrue(attempts.blockedFor("someone-else", IP).isPresent());
        assertTrue(attempts.blockedFor("someone-else", "10.0.0.2").isEmpty());
    }

    @Test
    void successDoesNotResetIpCounter() {
        // зловмисник не може «обнуляти» адресу, входячи у власний акаунт між спробами
        for (int u = 0; u < 10; u++) {
            fail("user" + u, IP, 3);
            attempts.recordSuccess("attacker");
        }
        assertTrue(attempts.blockedFor("attacker", IP).isPresent());
    }
}