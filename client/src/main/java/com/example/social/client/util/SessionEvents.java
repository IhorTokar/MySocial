package com.example.social.client.util;

import javafx.application.Platform;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Завершення сесії з будь-якого місця клієнта. Головне вікно (MainShellController) реєструє обробник,
 * який зупиняє фонові задачі й показує екран входу з поясненням.
 *
 * <p>Сервер відповідає 401, коли токен прострочений (через 24 год), відкликаний (зміна чи скидання пароля)
 * або акаунт заблоковано. Тоді немає сенсу показувати помилки в кожному розділі — треба увійти знову.
 */
public final class SessionEvents {

    public static final String EXPIRED_MESSAGE = "Сесія завершилась — увійдіть знову";

    private static volatile Consumer<String> logoutHandler;
    /** Кілька запитів можуть отримати 401 одночасно — вихід має статися один раз. */
    private static final AtomicBoolean expiring = new AtomicBoolean();

    private SessionEvents() {
    }

    /** Головне вікно відкрите: від цього моменту 401 повертає на екран входу. */
    public static void setLogoutHandler(Consumer<String> handler) {
        logoutHandler = handler;
        expiring.set(false);
    }

    public static void clearLogoutHandler() {
        logoutHandler = null;
    }

    /** Сервер відповів 401 на запит із токеном. Можна викликати з будь-якого потоку. */
    public static void sessionExpired() {
        Consumer<String> handler = logoutHandler;
        if (handler != null && expiring.compareAndSet(false, true)) {
            Platform.runLater(() -> handler.accept(EXPIRED_MESSAGE));
        }
    }

    /**
     * Вийти з повідомленням для екрана входу (null — без повідомлення).
     *
     * @return false, якщо головного вікна немає і вихід треба виконати самостійно
     */
    public static boolean logout(String message) {
        Consumer<String> handler = logoutHandler;
        if (handler == null) {
            return false;
        }
        Platform.runLater(() -> handler.accept(message));
        return true;
    }
}