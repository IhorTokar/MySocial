package com.example.social.client.util;

import com.example.social.client.service.AdminApiService.AdminApiException;
import javafx.application.Platform;

import java.util.function.Consumer;

/** Запит до сервера у віртуальному потоці з поверненням результату в потік інтерфейсу. */
public final class Async {

    @FunctionalInterface
    public interface Call {
        void run() throws Exception;
    }

    private Async() {
    }

    /**
     * @param call    робота у фоновому потоці; оновлення інтерфейсу всередині — через Platform.runLater
     * @param onError отримує зрозумілий текст помилки (у потоці інтерфейсу)
     * @param onDone  виконується завжди після завершення (може бути null)
     */
    public static void run(Call call, Consumer<String> onError, Runnable onDone) {
        Thread.ofVirtual().start(() -> {
            String error = null;
            try {
                call.run();
            } catch (AdminApiException e) {
                error = e.getMessage();
            } catch (Exception e) {
                error = "Не вдалося з'єднатись із сервером: " + e.getMessage();
            }
            String finalError = error;
            Platform.runLater(() -> {
                if (onDone != null) {
                    onDone.run();
                }
                if (finalError != null) {
                    onError.accept(finalError);
                }
            });
        });
    }
}