package com.example.social.server.service;

import com.example.social.server.exception.BadRequestException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Перевірка медіафайлів і посилань на них.
 *
 * <p>Завантаження: тип визначається за вмістом файлу, а не за заголовком Content-Type чи розширенням,
 * які надсилає клієнт, — інакше під виглядом «картинки» можна покласти HTML, SVG чи програму,
 * і сервер роздаватиме їх усім з /media/.
 *
 * <p>Посилання: у постах і повідомленнях дозволено лише файли, які завантажено на цей сервер.
 * Довільна адреса змусила б клієнти інших користувачів самих звертатися на чужий сервер
 * (розкриття IP-адреси, підміна чи величезний файл).
 */
public final class MediaFiles {

    /** Формат, який розпізнав ImageIO → розширення файлу на диску. */
    private static final Map<String, String> EXTENSIONS = Map.of(
            "png", ".png",
            "jpeg", ".jpg",
            "jpg", ".jpg",
            "gif", ".gif",
            "bmp", ".bmp");

    /** Захист від «бомб»: маленький файл із величезними розмірами з'їв би всю пам'ять при декодуванні. */
    private static final int MAX_SIDE = 10_000;

    /** Файл, збережений FileStorageService: /media/<UUID>.<розширення>. */
    private static final Pattern LOCAL_MEDIA_URL = Pattern.compile(
            "^/media/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.[a-z0-9]{2,5}$");

    private MediaFiles() {
    }

    /**
     * Розширення для збереження зображення, визначене за його вмістом (PNG, JPEG, GIF, BMP).
     *
     * @throws BadRequestException якщо це не зображення, формат не підтримується або воно завелике
     */
    public static String imageExtension(byte[] data) {
        if (data == null || data.length == 0) {
            throw new BadRequestException("Файл порожній");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw new BadRequestException("Це не зображення або формат не підтримується (PNG, JPEG, GIF, BMP)");
            }
            ImageReader reader = readers.next();
            try {
                String extension = EXTENSIONS.get(reader.getFormatName().toLowerCase(Locale.ROOT));
                if (extension == null) {
                    throw new BadRequestException("Формат " + reader.getFormatName()
                            + " не підтримується — збережіть зображення як PNG або JPEG");
                }
                reader.setInput(input, true, true);
                // розміри читаються із заголовка, до декодування пікселів
                if (reader.getWidth(0) > MAX_SIDE || reader.getHeight(0) > MAX_SIDE) {
                    throw new BadRequestException("Зображення завелике: щонайбільше " + MAX_SIDE
                            + " пікселів по кожній стороні");
                }
                return extension;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BadRequestException("Не вдалося прочитати зображення");
        }
    }

    /**
     * Посилання на медіа з запиту клієнта: null або порожнє — «без медіа»,
     * інакше лише файл, завантажений на цей сервер.
     *
     * @return нормалізоване посилання або null
     * @throws BadRequestException для зовнішніх адрес і будь-чого іншого
     */
    public static String requireLocalUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        if (!LOCAL_MEDIA_URL.matcher(trimmed).matches()) {
            throw new BadRequestException("Дозволені лише файли, завантажені на сервер (/media/…)");
        }
        return trimmed;
    }
}