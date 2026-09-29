package com.example.social.client.util;

import com.example.social.client.service.ApiClient;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

public class AvatarUtil {

    private static final Color[] PALETTE = {
            Color.web("#1D9BF0"), Color.web("#00BA7C"), Color.web("#F91880"),
            Color.web("#FF7A00"), Color.web("#7856FF"), Color.web("#536471")
    };

    private AvatarUtil() {
    }

    /**
     * Круглий аватар: спершу коло з літерою (запасний варіант),
     * поверх нього — фото, щойно воно завантажилось.
     */
    public static StackPane create(String name, String avatarUrl, double size) {
        StackPane pane = new StackPane();
        pane.setMinSize(size, size);
        pane.setPrefSize(size, size);
        pane.setMaxSize(size, size);

        Circle background = new Circle(size / 2, colorFor(name));
        Label initial = new Label(initialOf(name));
        initial.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: "
                + (int) (size * 0.42) + "px;");
        pane.getChildren().addAll(background, initial);

        if (avatarUrl != null && !avatarUrl.isBlank()) {
            attachImage(pane, avatarUrl, size);
        }
        return pane;
    }

    private static void attachImage(StackPane pane, String avatarUrl, double size) {
        String fullUrl = avatarUrl.startsWith("http") ? avatarUrl : ApiClient.BASE_URL + avatarUrl;

        Image image;
        try {
            // фонове завантаження + зменшення до розміру аватара (x2 для HiDPI)
            image = new Image(fullUrl, size * 2, size * 2, true, true, true);
        } catch (IllegalArgumentException e) {
            return; // некоректний URL: лишається кружок з літерою
        }

        Runnable showImage = () -> {
            if (image.isError() || image.getWidth() <= 0) {
                return;
            }
            // квадратна обрізка по центру, щоб не було спотворення
            double side = Math.min(image.getWidth(), image.getHeight());
            ImageView view = new ImageView(image);
            view.setViewport(new Rectangle2D(
                    (image.getWidth() - side) / 2, (image.getHeight() - side) / 2, side, side));
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setClip(new Circle(size / 2, size / 2, size / 2));
            pane.getChildren().add(view);
        };

        if (image.getProgress() >= 1.0) {
            showImage.run();
        } else {
            image.progressProperty().addListener((obs, oldValue, progress) -> {
                if (progress.doubleValue() >= 1.0) {
                    showImage.run();
                }
            });
        }
    }

    private static Color colorFor(String name) {
        int hash = name == null ? 0 : name.hashCode();
        return PALETTE[Math.floorMod(hash, PALETTE.length)];
    }

    private static String initialOf(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        int codePoint = name.trim().codePointAt(0);
        return new String(Character.toChars(codePoint)).toUpperCase();
    }
}