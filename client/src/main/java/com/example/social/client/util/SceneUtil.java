package com.example.social.client.util;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.stage.Stage;

public class SceneUtil {

    private SceneUtil() {
    }

    public static Scene create(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        ThemeManager.getInstance().applyTo(scene);
        ScreenshotUtil.install(scene);
        // F11 — увімкнути / вимкнути повноекранний режим
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F11), () -> {
            if (scene.getWindow() instanceof Stage stage) {
                stage.setFullScreen(!stage.isFullScreen());
            }
        });
        return scene;
    }

    /** Головне вікно відкривається розгорнутим на весь екран. */
    public static void setUpMainWindow(Stage stage) {
        stage.setMaximized(true);
        stage.setFullScreenExitHint("F11 або Esc — вийти з повноекранного режиму");
    }

    /**
     * Змінити екран у тому ж вікні (вхід → стрічка → вихід), не зменшуючи вікно:
     * якщо воно розгорнуте чи повноекранне, нова сцена отримує його поточний розмір.
     */
    public static void switchScene(Stage stage, Parent root, double width, double height) {
        boolean fullScreen = stage.isFullScreen();
        Scene current = stage.getScene();
        boolean keepSize = current != null && (fullScreen || stage.isMaximized());
        stage.setScene(create(root,
                keepSize ? current.getWidth() : width,
                keepSize ? current.getHeight() : height));
        if (fullScreen) {
            stage.setFullScreen(true);
        }
    }
}