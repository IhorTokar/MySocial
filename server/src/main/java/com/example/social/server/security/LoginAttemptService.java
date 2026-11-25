package com.example.social.server.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Обмеження невдалих спроб входу — захист від підбору пароля.
 *
 * <p>Лічильники ведуться окремо для логіна і для IP-адреси. Перша невдала спроба відкриває вікно
 * (за замовчуванням 15 хв); коли за вікно набирається ліміт, вхід блокується до кінця вікна:
 * <ul>
 *   <li>за логіном (5 спроб) — не дає перебирати паролі до одного акаунта;</li>
 *   <li>за IP (30 спроб) — не дає з однієї адреси перебирати багато акаунтів по кілька спроб.</li>
 * </ul>
 * Успішний вхід скидає лічильник логіна, але не IP: інакше зловмисник скидав би його,
 * входячи у власний акаунт між спробами.
 *
 * <p>Стан зберігається в пам'яті одного сервера й зникає після перезапуску — для однієї
 * інстанції цього достатньо. Блокування тимчасове, тож чужими невдалими спробами акаунт
 * можна «заморозити» щонайбільше на одне вікно.
 */
@Component
public class LoginAttemptService {

    /** Після такої кількості записів прострочені вікна прибираються, щоб пам'ять не росла. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int maxFailuresPerUser;
    private final int maxFailuresPerIp;
    private final Duration window;
    private final Clock clock;

    private final Map<String, Attempts> byUser = new ConcurrentHashMap<>();
    private final Map<String, Attempts> byIp = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptService(@Value("${security.login.max-failures-per-user:5}") int maxFailuresPerUser,
                               @Value("${security.login.max-failures-per-ip:30}") int maxFailuresPerIp,
                               @Value("${security.login.lockout-minutes:15}") long lockoutMinutes) {
        this(maxFailuresPerUser, maxFailuresPerIp, Duration.ofMinutes(lockoutMinutes), Clock.systemUTC());
    }

    LoginAttemptService(int maxFailuresPerUser, int maxFailuresPerIp, Duration window, Clock clock) {
        this.maxFailuresPerUser = maxFailuresPerUser;
        this.maxFailuresPerIp = maxFailuresPerIp;
        this.window = window;
        this.clock = clock;
    }

    /** Скільки ще чекати, якщо вхід для цього логіна або IP зараз заблоковано; інакше — порожньо. */
    public Optional<Duration> blockedFor(String username, String ip) {
        Instant now = clock.instant();
        Duration user = remaining(byUser, key(username), maxFailuresPerUser, now);
        Duration address = remaining(byIp, ip, maxFailuresPerIp, now);
        Duration longest = user.compareTo(address) >= 0 ? user : address;
        return longest.isZero() ? Optional.empty() : Optional.of(longest);
    }

    public void recordFailure(String username, String ip) {
        Instant now = clock.instant();
        increment(byUser, key(username), now);
        if (ip != null) {
            increment(byIp, ip, now);
        }
        if (byUser.size() + byIp.size() > CLEANUP_THRESHOLD) {
            byUser.values().removeIf(a -> a.expired(now, window));
            byIp.values().removeIf(a -> a.expired(now, window));
        }
    }

    public void recordSuccess(String username) {
        byUser.remove(key(username));
    }

    private Duration remaining(Map<String, Attempts> map, String key, int limit, Instant now) {
        if (key == null) {
            return Duration.ZERO;
        }
        Attempts attempts = map.get(key);
        if (attempts == null || attempts.expired(now, window) || attempts.failures < limit) {
            return Duration.ZERO;
        }
        return Duration.between(now, attempts.start.plus(window));
    }

    private void increment(Map<String, Attempts> map, String key, Instant now) {
        map.compute(key, (k, old) -> old == null || old.expired(now, window)
                ? new Attempts(now, 1)
                : new Attempts(old.start, old.failures + 1));
    }

    /** Логін без урахування регістру й пробілів: «Medoko» і «medoko » — той самий лічильник. */
    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private record Attempts(Instant start, int failures) {
        boolean expired(Instant now, Duration window) {
            return !now.isBefore(start.plus(window));
        }
    }
}