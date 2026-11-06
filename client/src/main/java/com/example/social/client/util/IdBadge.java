package com.example.social.client.util;

import javafx.scene.control.Label;

/**
 * Невелика мітка з ID (поста, користувача, коментаря) для адміністратора.
 * Видима лише адміну і лише коли в налаштуваннях увімкнено «Показувати ID».
 * Клік копіює числовий ID у буфер обміну — щоб вставити його в адмін-панель.
 */
public final class IdBadge {

    private IdBadge() {
    }

    public static Label create() {
        Label label = new Label();
        configure(label);
        return label;
    }

    /** Налаштовує вже наявну мітку (наприклад, оголошену у FXML). */
    public static void configure(Label label) {
        label.getStyleClass().add("id-badge");
        CopyOnClick.install(label, "Натисніть, щоб скопіювати ID");

        if (SessionManager.getInstance().isAdmin()) {
            label.visibleProperty().bind(AppSettings.getInstance().showIdsProperty()
                    .and(label.textProperty().isNotEmpty()));
        } else {
            label.setVisible(false);
        }
        label.managedProperty().bind(label.visibleProperty());
    }

    /** Показує текст на мітці; copyValue — те, що потрапить у буфер обміну при кліку. */
    public static void set(Label label, String text, Object copyValue) {
        label.setText(text == null ? "" : text);
        CopyOnClick.setValue(label, copyValue);
    }
}