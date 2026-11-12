package com.example.social.client.component;

import com.example.social.client.service.ApiClient;
import javafx.geometry.Orientation;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Обкладинка профілю (3:1). Висота = ширина / 3 у заданих межах; зображення заповнює всю
 * площу без спотворень (зайве обрізається). focus — яку частину зайвого показувати: 0 — верх/ліво, 1 — низ/право.
 */
public class CoverPane extends Region {

    public static final double RATIO = 3.0;
    public static final int OUTPUT_WIDTH = 1500;
    public static final int MIN_WIDTH = 600;
    public static final int MIN_HEIGHT = 200;

    private final ImageView view = new ImageView();
    private final Rectangle clip = new Rectangle();
    private double minCoverHeight = 0;
    private double maxCoverHeight = Double.MAX_VALUE;
    private double focus = 0.5;

    public CoverPane() {
        getStyleClass().add("cover-pane");
        view.setSmooth(true);
        view.setPreserveRatio(false);
        getChildren().add(view);
        clip.setArcWidth(20);
        clip.setArcHeight(20);
        setClip(clip);
    }

    /** Межі висоти: у профілі обкладинка не повинна займати пів екрана на широкому вікні. */
    public void setHeightLimits(double min, double max) {
        this.minCoverHeight = min;
        this.maxCoverHeight = max;
        requestLayout();
    }

    public void setFocus(double focus) {
        this.focus = Math.max(0, Math.min(1, focus));
        requestLayout();
    }

    public double getFocus() {
        return focus;
    }

    public Image getImage() {
        return view.getImage();
    }

    /** Локальне зображення (попередній перегляд перед завантаженням). */
    public void setImage(Image image) {
        view.setImage(image);
        requestLayout();
    }

    /** Обкладинка із сервера; null — порожня (лишається фон із CSS). */
    public void load(String url) {
        focus = 0.5;
        if (url == null || url.isBlank()) {
            setImage(null);
            return;
        }
        String fullUrl = url.startsWith("http") ? url : ApiClient.BASE_URL + url;
        Image image = new Image(fullUrl, true);
        // розміри відомі лише після завантаження — тоді й перераховується видима частина
        image.progressProperty().addListener((obs, old, progress) -> {
            if (progress.doubleValue() >= 1.0) {
                requestLayout();
            }
        });
        setImage(image);
    }

    // ===== Розміри =====

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;   // висота залежить від ширини
    }

    @Override
    protected double computePrefHeight(double width) {
        double w = width > 0 ? width : getWidth();
        if (w <= 0) {
            return minCoverHeight;
        }
        return Math.max(minCoverHeight, Math.min(maxCoverHeight, w / RATIO));
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected double computeMaxHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected double computePrefWidth(double height) {
        return 0;   // ширину задає батьківський контейнер
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        clip.setWidth(w);
        clip.setHeight(h);

        Image image = view.getImage();
        if (image == null || image.isError() || image.getWidth() <= 0 || w <= 0 || h <= 0) {
            view.setVisible(false);
            return;
        }
        view.setVisible(true);
        view.setViewport(viewport(image.getWidth(), image.getHeight(), w / h, focus));
        view.setFitWidth(w);
        view.setFitHeight(h);
        view.relocate(0, 0);
    }

    /** Найбільша ділянка зображення з потрібним співвідношенням сторін; зайве обрізається з урахуванням focus. */
    public static Rectangle2D viewport(double imageWidth, double imageHeight, double aspect, double focus) {
        if (imageWidth / imageHeight > aspect) {
            double width = imageHeight * aspect;
            return new Rectangle2D((imageWidth - width) * focus, 0, width, imageHeight);
        }
        double height = imageWidth / aspect;
        return new Rectangle2D(0, (imageHeight - height) * focus, imageWidth, height);
    }

    /** Чи вистачає роздільності, щоб вирізати обкладинку 3:1. */
    public static boolean isLargeEnough(Image image) {
        Rectangle2D crop = viewport(image.getWidth(), image.getHeight(), RATIO, 0.5);
        return crop.getWidth() >= MIN_WIDTH && crop.getHeight() >= MIN_HEIGHT;
    }

    /** Чи зображення відрізняється від 3:1 настільки, що є що зсувати. */
    public static boolean needsPositioning(Image image) {
        return Math.abs(image.getWidth() / image.getHeight() - RATIO) > 0.03;
    }

    /**
     * Вирізати рівно ту частину, яку видно в попередньому перегляді, зменшити до 1500×500
     * і записати в тимчасовий PNG для завантаження на сервер.
     */
    public static Path exportCrop(Image image, double focus) throws IOException {
        PixelReader reader = image.getPixelReader();
        if (reader == null) {
            throw new IOException("Зображення ще не завантажилось");
        }
        Rectangle2D crop = viewport(image.getWidth(), image.getHeight(), RATIO, focus);
        int sx = (int) Math.round(crop.getMinX());
        int sy = (int) Math.round(crop.getMinY());
        int sw = Math.min((int) crop.getWidth(), (int) image.getWidth() - sx);
        int sh = Math.min((int) crop.getHeight(), (int) image.getHeight() - sy);

        int[] pixels = new int[sw * sh];
        reader.getPixels(sx, sy, sw, sh, PixelFormat.getIntArgbInstance(), pixels, 0, sw);
        BufferedImage source = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, sw, sh, pixels, 0, sw);

        int outW = Math.min(OUTPUT_WIDTH, sw);
        int outH = (int) Math.round(outW / RATIO);
        BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, outW, outH);
            g.drawImage(source, 0, 0, outW, outH, null);
        } finally {
            g.dispose();
        }

        Path file = Files.createTempFile("cover-", ".png");
        ImageIO.write(out, "png", file.toFile());
        return file;
    }
}