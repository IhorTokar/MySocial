package com.example.social.client.component;

import com.example.social.client.util.ThemeManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Перегляд зображення поверх головного вікна: колесо миші — масштаб відносно курсора,
 * перетягування — зсув, подвійний клік — «вмістити» ↔ 100 %, Esc або клік по тлу — закрити.
 */
public final class ImageViewer {

    private static final double MIN_ZOOM = 0.1;
    private static final double MAX_ZOOM = 8.0;
    private static final double WHEEL_STEP = 1.15;
    private static final double BUTTON_STEP = 1.4;

    private final Stage stage = new Stage(StageStyle.UNDECORATED);
    private final Pane canvas = new Pane();
    private final ImageView view = new ImageView();
    private final Label zoomLabel = new Label();
    private final Image image;

    /** Поточний масштаб і положення лівого верхнього кута зображення в canvas. */
    private double scale = 1;
    private double x;
    private double y;
    private double dragStartX;
    private double dragStartY;
    private boolean dragged;
    /** true — зображення «вміщене» у вікно: при зміні розміру вікна вміщується знову. */
    private boolean fitted;

    private ImageViewer(Window owner, String url) {
        image = new Image(url, true);   // повна роздільність, у фоні

        view.setImage(image);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setManaged(false);
        canvas.getChildren().add(view);

        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(48, 48);
        progress.visibleProperty().bind(image.progressProperty().lessThan(1));

        Label error = new Label("Не вдалося завантажити зображення");
        error.setTextFill(Color.WHITE);
        error.visibleProperty().bind(image.errorProperty());
        progress.setMouseTransparent(true);
        error.setMouseTransparent(true);

        StackPane root = new StackPane(canvas, progress, error, toolbar());
        root.setStyle("-fx-background-color: rgba(0, 0, 0, 0.92);");
        StackPane.setAlignment(root.getChildren().get(3), Pos.TOP_RIGHT);

        installMouse();
        image.progressProperty().addListener((obs, old, p) -> {
            if (p.doubleValue() >= 1 && !image.isError()) {
                fit();
            }
        });
        canvas.widthProperty().addListener((obs, old, w) -> fitIfNotZoomed());
        canvas.heightProperty().addListener((obs, old, h) -> fitIfNotZoomed());

        Scene scene = new Scene(root);
        scene.setFill(Color.BLACK);
        ThemeManager.getInstance().applyTo(scene);
        scene.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ESCAPE -> stage.close();
                case PLUS, ADD, EQUALS -> zoomAtCenter(BUTTON_STEP);
                case MINUS, SUBTRACT -> zoomAtCenter(1 / BUTTON_STEP);
                case DIGIT0, NUMPAD0 -> fit();
                default -> { }
            }
        });

        stage.setScene(scene);
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        // поверх усього головного вікна
        stage.setX(owner.getX());
        stage.setY(owner.getY());
        stage.setWidth(owner.getWidth());
        stage.setHeight(owner.getHeight());
    }

    /** Відкрити зображення поверх вікна, якому належить owner. */
    public static void show(Window owner, String url) {
        if (owner == null || url == null || url.isBlank()) {
            return;
        }
        new ImageViewer(owner, url).stage.show();
    }

    // ===== Панель кнопок =====

    private HBox toolbar() {
        zoomLabel.setTextFill(Color.WHITE);
        zoomLabel.setMinWidth(52);
        zoomLabel.setAlignment(Pos.CENTER);

        HBox bar = new HBox(6,
                iconButton("fas-search-minus", "Зменшити (−)", () -> zoomAtCenter(1 / BUTTON_STEP)),
                zoomLabel,
                iconButton("fas-search-plus", "Збільшити (+)", () -> zoomAtCenter(BUTTON_STEP)),
                iconButton("fas-compress", "Вмістити у вікно (0)", this::fit),
                iconButton("fas-expand", "Справжній розмір (100 %)", this::actualSize),
                iconButton("fas-times", "Закрити (Esc)", stage::close));
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(12));
        bar.setMaxSize(HBox.USE_PREF_SIZE, HBox.USE_PREF_SIZE);
        bar.setPickOnBounds(false);
        return bar;
    }

    private static Button iconButton(String icon, String tooltip, Runnable action) {
        FontIcon graphic = new FontIcon(icon);
        graphic.setIconColor(Color.WHITE);
        graphic.setIconSize(16);
        Button button = new Button(null, graphic);
        button.setStyle("-fx-background-color: rgba(255, 255, 255, 0.15); -fx-background-radius: 18;"
                + " -fx-min-width: 36; -fx-min-height: 36; -fx-cursor: hand;");
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(e -> action.run());
        button.setFocusTraversable(false);
        return button;
    }

    // ===== Миша =====

    private void installMouse() {
        canvas.setOnScroll(e -> {
            if (e.getDeltaY() != 0) {
                zoomAt(e.getDeltaY() > 0 ? WHEEL_STEP : 1 / WHEEL_STEP, e.getX(), e.getY());
            }
        });
        canvas.setOnMousePressed(e -> {
            dragStartX = e.getX() - x;
            dragStartY = e.getY() - y;
            dragged = false;
        });
        canvas.setOnMouseDragged(e -> {
            dragged = true;
            x = e.getX() - dragStartX;
            y = e.getY() - dragStartY;
            apply();
        });
        canvas.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY || dragged) {
                return;
            }
            if (e.getClickCount() == 2) {
                if (Math.abs(scale - 1) < 0.01) {
                    fit();
                } else {
                    zoomAt(1 / scale, e.getX(), e.getY());   // 100 % у точці кліку
                }
            } else if (!view.getBoundsInParent().contains(e.getX(), e.getY())) {
                stage.close();   // клік по темному тлу
            }
        });
    }

    // ===== Масштаб =====

    private boolean loaded() {
        return image.getProgress() >= 1 && !image.isError() && image.getWidth() > 0;
    }

    /** Вмістити повністю (дрібні зображення не розтягуються понад 100 %) і відцентрувати. */
    private void fit() {
        if (!loaded() || canvas.getWidth() <= 0) {
            return;
        }
        scale = Math.min(1, Math.min(canvas.getWidth() / image.getWidth(), canvas.getHeight() / image.getHeight()));
        center();
    }

    private void actualSize() {
        if (loaded()) {
            scale = 1;
            center();
        }
    }

    private void fitIfNotZoomed() {
        if (fitted || scale == 1) {
            fit();
        }
    }

    private void center() {
        x = (canvas.getWidth() - image.getWidth() * scale) / 2;
        y = (canvas.getHeight() - image.getHeight() * scale) / 2;
        fitted = true;
        apply();
    }

    private void zoomAtCenter(double factor) {
        zoomAt(factor, canvas.getWidth() / 2, canvas.getHeight() / 2);
    }

    /** Масштабувати так, щоб точка під курсором лишилася на місці. */
    private void zoomAt(double factor, double px, double py) {
        if (!loaded()) {
            return;
        }
        double newScale = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, scale * factor));
        double k = newScale / scale;
        x = px - (px - x) * k;
        y = py - (py - y) * k;
        scale = newScale;
        fitted = false;
        apply();
    }

    private void apply() {
        if (!loaded()) {
            return;
        }
        view.setFitWidth(image.getWidth() * scale);
        view.setFitHeight(image.getHeight() * scale);
        view.relocate(x, y);
        zoomLabel.setText(Math.round(scale * 100) + " %");
    }
}