package com.example.social.server.service;

/**
 * З якого тексту рахується вектор поста: заголовок і текст разом. Часто головне слово поста стоїть
 * саме в заголовку («Борщ за бабусиним рецептом»), а текст лише розповідає подробиці («Буряк запікаю
 * окремо…») — без заголовка пошук за змістом і схожі пости такий пост не знаходили б.
 *
 * <p>Службові пости експериментів (однаковий технічний заголовок на всіх) рахують вектор лише з тексту.
 */
public final class EmbeddingText {

    private EmbeddingText() {
    }

    /** «Заголовок. Текст»; без заголовка — просто текст; крапка не додається, якщо заголовок уже нею закінчується. */
    public static String of(String label, String text) {
        String body = text == null ? "" : text.trim();
        String title = label == null ? "" : label.trim();
        if (title.isEmpty()) {
            return body;
        }
        if (body.isEmpty()) {
            return title;
        }
        char last = title.charAt(title.length() - 1);
        boolean ends = last == '.' || last == '!' || last == '?' || last == '…' || last == ':';
        return title + (ends ? " " : ". ") + body;
    }
}