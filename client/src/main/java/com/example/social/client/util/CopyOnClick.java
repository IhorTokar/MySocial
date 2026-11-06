package com.example.social.client.util;

import javafx.animation.PauseTransition;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.util.Duration;

/**
 * Копіювання тексту мітки в буфер обміну по кліку (ID, теги).
 * Після копіювання мітка ~1 секунду має стиль .copied — видно, що клік спрацював.
 */
public final class CopyOnClick {

    private static final String VALUE_KEY = "copyOnClick.value";
    private static final Duration FEEDBACK = Duration.seconds(1.2);

    private CopyOnClick() {
    }

    public static void install(Label label, String tooltipText) {
        label.setCursor(Cursor.HAND);
        Tooltip tooltip = new Tooltip(tooltipText);
        label.setTooltip(tooltip);

        label.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            Object value = label.getProperties().get(VALUE_KEY);
            if (value == null) {
                return;
            }
            ClipboardContent content = new ClipboardContent();
            content.putString(value.toString());
            Clipboard.getSystemClipboard().setContent(content);
            e.consume();   // клік не доходить до картки поста

            if (!label.getStyleClass().contains("copied")) {
                label.getStyleClass().add("copied");
            }
            tooltip.setText("Скопійовано: " + value);
            PauseTransition reset = new PauseTransition(FEEDBACK);
            reset.setOnFinished(done -> {
                label.getStyleClass().remove("copied");
                tooltip.setText(tooltipText);
            });
            reset.play();
        });
    }

    /** Що саме копіюється (може відрізнятися від тексту мітки: «пост #89» → «89»). */
    public static void setValue(Label label, Object value) {
        label.getProperties().put(VALUE_KEY, value);
    }
}