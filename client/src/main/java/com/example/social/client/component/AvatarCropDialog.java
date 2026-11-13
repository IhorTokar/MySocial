package com.example.social.client.component;

import com.example.social.client.util.ImageCrops;
import com.example.social.client.util.SceneUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Кадрування аватара: квадратна ділянка з круглою рамкою (аватари в застосунку круглі).
 * Перетягування — зсув, повзунок або колесо — масштаб. Результат — PNG до 512×512.
 */
public final class AvatarCropDialog {

    private static final double VIEW = 320;
    private static final int OUTPUT = 512;
    private static final double MAX_ZOOM = 5;

    private final Stage stage = new Stage();
    private final Image image;
    private final ImageView view = new ImageView();
    private final Slider zoomSlider = new Slider(1, MAX_ZOOM, 1);
    private final Label errorLabel = new Label();

    /** Масштаб, за якого зображення якраз покриває квадрат (zoom = 1). */
    private final double baseScale;
    private double scale;
    /** Лівий верхній кут зображення в координатах квадрата. */
    private double x;
    private double y;
    private double dragX;
    private double dragY;
    private Path result;

    private AvatarCropDialog(Window owner, Image image) {
        this.image = image;
        this.baseScale = Math.max(VIEW / image.getWidth(), VIEW / image.getHeight());
        this.scale = baseScale;
        this.x = (VIEW - image.getWidth() * scale) / 2;
        this.y = (VIEW - image.getHeight() * scale) / 2;

        Label title = new Label("Оберіть, яка частина фото буде аватаром");
        title.getStyleClass().add("post-title");
        Label hint = new Label("Перетягуйте фото мишею; масштаб — повзунком або колесом.");
        hint.getStyleClass().add("secondary-label");
        hint.setWrapText(true);

        zoomSlider.valueProperty().addListener((obs, old, zoom) -> zoomAroundCenter(zoom.doubleValue()));
        HBox.setHgrow(zoomSlider, Priority.ALWAYS);
        HBox zoomRow = new HBox(8, new FontIcon("fas-search-minus"), zoomSlider, new FontIcon("fas-search-plus"));
        zoomRow.setAlignment(Pos.CENTER);
        zoomRow.setMaxWidth(VIEW);

        errorLabel.getStyleClass().add("error-label");
        errorLabel.setWrapText(true);

        Button cancel = new Button("Скасувати");
        cancel.getStyleClass().setAll("button", "button-secondary");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> stage.close());
        Button save = new Button("Зберегти аватар");
        save.getStyleClass().setAll("button", "button-primary");
        save.setDefaultButton(true);
        save.setOnAction(e -> save());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, spacer, cancel, save);
        buttons.setMaxWidth(VIEW);

        VBox root = new VBox(12, title, hint, cropArea(), zoomRow, errorLabel, buttons);
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(20));

        stage.setTitle("Кадрування аватара");
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(false);
        stage.setScene(SceneUtil.create(root, VIEW + 60, VIEW + 260));
        apply();
    }

    /** @return тимчасовий PNG з обраною ділянкою або null, якщо користувач скасував */
    public static Path show(Window owner, Image image) {
        AvatarCropDialog dialog = new AvatarCropDialog(owner, image);
        dialog.stage.showAndWait();
        return dialog.result;
    }

    // ===== Ділянка кадрування =====

    private Pane cropArea() {
        view.setImage(image);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setManaged(false);

        // затемнення поза колом + біла рамка кола; клікам не заважають
        Shape mask = Shape.subtract(new Rectangle(VIEW, VIEW), new Circle(VIEW / 2, VIEW / 2, VIEW / 2));
        mask.setFill(Color.rgb(0, 0, 0, 0.55));
        mask.setMouseTransparent(true);
        Circle ring = new Circle(VIEW / 2, VIEW / 2, VIEW / 2 - 1);
        ring.setFill(Color.TRANSPARENT);
        ring.setStroke(Color.WHITE);
        ring.setStrokeWidth(2);
        ring.setMouseTransparent(true);

        Pane area = new Pane(view, mask, ring);
        area.setMinSize(VIEW, VIEW);
        area.setPrefSize(VIEW, VIEW);
        area.setMaxSize(VIEW, VIEW);
        area.setClip(new Rectangle(VIEW, VIEW));
        area.setCursor(Cursor.MOVE);
        area.setStyle("-fx-background-color: #000000;");

        area.setOnMousePressed(e -> {
            dragX = e.getX() - x;
            dragY = e.getY() - y;
        });
        area.setOnMouseDragged(e -> {
            x = e.getX() - dragX;
            y = e.getY() - dragY;
            apply();
        });
        area.setOnScroll(e -> {
            if (e.getDeltaY() != 0) {
                zoomSlider.setValue(zoomSlider.getValue() * (e.getDeltaY() > 0 ? 1.1 : 1 / 1.1));
            }
        });
        return area;
    }

    /** Масштабувати відносно центру квадрата — центр кадру лишається на місці. */
    private void zoomAroundCenter(double zoom) {
        double newScale = baseScale * zoom;
        double c = VIEW / 2;
        x = c - (c - x) * newScale / scale;
        y = c - (c - y) * newScale / scale;
        scale = newScale;
        apply();
    }

    /** Зображення завжди повністю покриває квадрат — порожніх країв в аватарі не буде. */
    private void apply() {
        double w = image.getWidth() * scale;
        double h = image.getHeight() * scale;
        x = Math.min(0, Math.max(VIEW - w, x));
        y = Math.min(0, Math.max(VIEW - h, y));
        view.setFitWidth(w);
        view.setFitHeight(h);
        view.relocate(x, y);
    }

    private void save() {
        Rectangle2D area = new Rectangle2D(-x / scale, -y / scale, VIEW / scale, VIEW / scale);
        try {
            result = ImageCrops.toPng(image, area, OUTPUT, "avatar-");
            stage.close();
        } catch (IOException e) {
            errorLabel.setText("Не вдалося підготувати аватар: " + e.getMessage());
        }
    }
}