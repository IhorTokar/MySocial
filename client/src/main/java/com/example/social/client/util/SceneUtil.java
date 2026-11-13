package com.example.social.client.util;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.stage.Screen;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;

public class SceneUtil {

    /** Наскільки вікно може бути меншим за робочу область екрана, щоб ще вважатися «на весь екран» (рамки). */
    private static final double SIZE_TOLERANCE = 80;
    private static final boolean LINUX = System.getProperty("os.name", "").toLowerCase().contains("linux");

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

    /**
     * Головне вікно відкривається на весь екран. На Linux — розтягуванням на робочу область,
     * а не прапорцем «розгорнуте»: KWin (KDE, Wayland/XWayland) при відкритті модального вікна
     * повертає розгорнуте вікно до «звичайного» розміру, а просто велике вікно не чіпає.
     */
    public static void setUpMainWindow(Stage stage) {
        if (LINUX) {
            fillScreen(stage, Screen.getPrimary());
        } else {
            stage.setMaximized(true);
        }
        stage.setFullScreenExitHint("F11 або Esc — вийти з повноекранного режиму");
        keepStateWhileDialogsOpen(stage);
    }

    /**
     * Запасний захист (напр. користувач сам розгорнув вікно подвійним кліком по заголовку):
     * перед першим діалогом запам'ятовується стан головного вікна, після закриття останнього — повертається.
     */
    private static void keepStateWhileDialogsOpen(Stage stage) {
        boolean[] wanted = {false, false};   // {на весь екран, повноекранне}

        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            boolean opened = false;
            boolean closed = false;
            while (change.next()) {
                // підказки, контекстні меню й списки ComboBox теж є вікнами — їх не враховуємо
                for (Window added : change.getAddedSubList()) {
                    opened |= added != stage && !(added instanceof PopupWindow);
                }
                for (Window removed : change.getRemoved()) {
                    closed |= removed != stage && !(removed instanceof PopupWindow);
                }
            }
            if (!stage.isShowing()) {
                return;
            }
            // відкрито перший діалог: розмір головного вікна ще не змінено — фіксуємо стан
            if (opened && countDialogs(stage) == 1) {
                wanted[0] = stage.isMaximized() || fillsScreen(stage);
                wanted[1] = stage.isFullScreen();
            }
            // закрито останній: KWin змінює розмір трохи згодом, тому перевірка — після двох циклів подій
            if (closed && countDialogs(stage) == 0) {
                Platform.runLater(() -> Platform.runLater(() -> restore(stage, wanted[0], wanted[1])));
            }
        });
    }

    private static void restore(Stage stage, boolean wantFill, boolean wantFullScreen) {
        if (wantFullScreen && !stage.isFullScreen()) {
            stage.setFullScreen(true);
        } else if (wantFill && !stage.isFullScreen() && !fillsScreen(stage)) {
            System.out.println("[window] вікно зменшилось після діалогу (" + (int) stage.getWidth() + "×"
                    + (int) stage.getHeight() + ") — повертаю на весь екран");
            if (stage.isMaximized()) {
                stage.setMaximized(false);   // інакше віконний менеджер знову «відновить» його при наступному діалозі
            }
            fillScreen(stage, screenOf(stage));
        }
    }

    private static void fillScreen(Stage stage, Screen screen) {
        Rectangle2D area = screen.getVisualBounds();
        stage.setX(area.getMinX());
        stage.setY(area.getMinY());
        stage.setWidth(area.getWidth());
        stage.setHeight(area.getHeight());
    }

    private static boolean fillsScreen(Stage stage) {
        Rectangle2D area = screenOf(stage).getVisualBounds();
        return stage.getWidth() >= area.getWidth() - SIZE_TOLERANCE
                && stage.getHeight() >= area.getHeight() - SIZE_TOLERANCE;
    }

    private static Screen screenOf(Stage stage) {
        var screens = Screen.getScreensForRectangle(stage.getX(), stage.getY(),
                Math.max(1, stage.getWidth()), Math.max(1, stage.getHeight()));
        return screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
    }

    private static int countDialogs(Stage main) {
        int count = 0;
        for (Window window : Window.getWindows()) {
            if (window != main && window.isShowing() && !(window instanceof PopupWindow)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Змінити екран у тому ж вікні (вхід → стрічка → вихід), не зменшуючи вікно:
     * якщо воно розгорнуте чи повноекранне, нова сцена отримує його поточний розмір.
     */
    public static void switchScene(Stage stage, Parent root, double width, double height) {
        boolean fullScreen = stage.isFullScreen();
        Scene current = stage.getScene();
        boolean keepSize = current != null && (fullScreen || stage.isMaximized() || fillsScreen(stage));
        stage.setScene(create(root,
                keepSize ? current.getWidth() : width,
                keepSize ? current.getHeight() : height));
        if (fullScreen) {
            stage.setFullScreen(true);
        }
    }
}