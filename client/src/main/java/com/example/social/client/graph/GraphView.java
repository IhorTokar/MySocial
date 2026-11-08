package com.example.social.client.graph;

import com.example.social.shared.dto.GraphDataDto;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Полотно з графом підписок: вузли кольору своєї спільноти, ребра FOLLOWS.
 * Колесо миші — масштаб відносно курсора, перетягування — зсув, клік — вибір вузла,
 * подвійний клік — «відкрити» вузол (обробник задає контролер).
 */
public class GraphView extends Region {

    private static final Color OUTGOING = Color.web("#1D9BF0");   // на кого підписаний вибраний
    private static final Color INCOMING = Color.web("#00BA7C");   // хто підписаний на вибраного
    private static final double MIN_ZOOM = 0.3;
    private static final double MAX_ZOOM = 12;

    private final Canvas canvas = new Canvas();
    /** Невидима мітка зі стилем тексту поста: з неї береться колір тексту поточної теми. */
    private final Label textProbe = new Label();

    private List<GraphDataDto.Node> nodes = List.of();
    private int[][] edges = new int[0][];
    private double[][] positions = new double[0][];
    private Color[] colors = new Color[0];
    private int[] degree = new int[0];
    private List<Set<Integer>> neighbours = List.of();
    private final Map<Long, Integer> indexByUserId = new HashMap<>();

    private double baseScale = 1;
    private double scale = 1;
    private double offsetX;
    private double offsetY;
    private boolean fitted;

    private int hovered = -1;
    private int selected = -1;
    private Long highlightedCommunity;
    private boolean showLabels;

    private double pressX;
    private double pressY;
    private boolean dragged;

    private Consumer<GraphDataDto.Node> onNodeSelected = node -> { };
    private Consumer<GraphDataDto.Node> onNodeOpened = node -> { };

    public GraphView() {
        getStyleClass().add("graph-view");
        textProbe.getStyleClass().add("post-text");
        textProbe.setVisible(false);
        textProbe.setManaged(false);
        getChildren().addAll(textProbe, canvas);

        // Малюнок не виходить за межі панелі
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        setOnScroll(this::handleScroll);
        setOnMousePressed(this::handlePressed);
        setOnMouseDragged(this::handleDragged);
        setOnMouseClicked(this::handleClicked);
        setOnMouseMoved(this::handleMoved);
    }

    // ===== Дані =====

    /**
     * @param positions координати з ForceLayout у просторі 0..ForceLayout.SIZE
     * @param colors    колір кожного вузла (в тому ж порядку, що nodes)
     */
    public void setGraph(List<GraphDataDto.Node> nodes, int[][] edges, double[][] positions, Color[] colors) {
        this.nodes = nodes;
        this.edges = edges;
        this.positions = positions;
        this.colors = colors;

        indexByUserId.clear();
        for (int i = 0; i < nodes.size(); i++) {
            indexByUserId.put(nodes.get(i).userId(), i);
        }
        degree = new int[nodes.size()];
        neighbours = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            neighbours.add(new HashSet<>());
        }
        for (int[] e : edges) {
            degree[e[0]]++;
            degree[e[1]]++;
            neighbours.get(e[0]).add(e[1]);
            neighbours.get(e[1]).add(e[0]);
        }

        hovered = -1;
        selected = -1;
        fitted = false;
        requestLayout();
        redraw();
    }

    /** Нові координати (після «Перерахувати розкладку») без зміни вибору й масштабу. */
    public void setPositions(double[][] positions) {
        this.positions = positions;
        redraw();
    }

    public void setShowLabels(boolean showLabels) {
        this.showLabels = showLabels;
        redraw();
    }

    /** Підсвітити одну спільноту (решта блідне); null — зняти підсвітку. */
    public void setHighlightedCommunity(Long community) {
        this.highlightedCommunity = community;
        redraw();
    }

    public Long getHighlightedCommunity() {
        return highlightedCommunity;
    }

    public void setOnNodeSelected(Consumer<GraphDataDto.Node> handler) {
        this.onNodeSelected = handler;
    }

    public void setOnNodeOpened(Consumer<GraphDataDto.Node> handler) {
        this.onNodeOpened = handler;
    }

    /** Вибрати вузол за ID користувача і показати його в центрі. false — такого вузла немає. */
    public boolean selectUser(long userId) {
        Integer index = indexByUserId.get(userId);
        if (index == null) {
            return false;
        }
        select(index);
        offsetX = getWidth() / 2 - positions[index][0] * scale;
        offsetY = getHeight() / 2 - positions[index][1] * scale;
        redraw();
        return true;
    }

    /** Повернути початковий масштаб, щоб було видно весь граф. */
    public void fitToView() {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        baseScale = Math.min(getWidth(), getHeight()) / ForceLayout.SIZE * 0.95;
        scale = baseScale;
        offsetX = (getWidth() - ForceLayout.SIZE * scale) / 2;
        offsetY = (getHeight() - ForceLayout.SIZE * scale) / 2;
        fitted = true;
        redraw();
    }

    // ===== Розмітка =====

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        if (!fitted && !nodes.isEmpty() && getWidth() > 0 && getHeight() > 0) {
            fitToView();
        }
        redraw();
    }

    // ===== Миша =====

    private void handleScroll(ScrollEvent e) {
        if (e.getDeltaY() == 0) {
            return;
        }
        double factor = e.getDeltaY() > 0 ? 1.15 : 1 / 1.15;
        double next = Math.max(baseScale * MIN_ZOOM, Math.min(baseScale * MAX_ZOOM, scale * factor));
        factor = next / scale;
        // Масштаб відносно курсора: точка під мишею лишається на місці
        offsetX = e.getX() - (e.getX() - offsetX) * factor;
        offsetY = e.getY() - (e.getY() - offsetY) * factor;
        scale = next;
        redraw();
        e.consume();
    }

    private void handlePressed(MouseEvent e) {
        pressX = e.getX();
        pressY = e.getY();
        dragged = false;
    }

    private void handleDragged(MouseEvent e) {
        double dx = e.getX() - pressX;
        double dy = e.getY() - pressY;
        if (Math.abs(dx) + Math.abs(dy) > 2) {
            dragged = true;
        }
        offsetX += dx;
        offsetY += dy;
        pressX = e.getX();
        pressY = e.getY();
        setCursor(Cursor.MOVE);
        redraw();
    }

    private void handleClicked(MouseEvent e) {
        setCursor(hovered >= 0 ? Cursor.HAND : Cursor.DEFAULT);
        if (dragged || e.getButton() != MouseButton.PRIMARY) {
            return;
        }
        int index = hit(e.getX(), e.getY());
        select(index);
        if (index >= 0 && e.getClickCount() == 2) {
            onNodeOpened.accept(nodes.get(index));
        }
    }

    private void handleMoved(MouseEvent e) {
        int index = hit(e.getX(), e.getY());
        if (index != hovered) {
            hovered = index;
            setCursor(index >= 0 ? Cursor.HAND : Cursor.DEFAULT);
            redraw();
        }
    }

    private void select(int index) {
        selected = index;
        onNodeSelected.accept(index >= 0 ? nodes.get(index) : null);
        redraw();
    }

    /** Найближчий вузол під курсором (з невеликим запасом), або -1. */
    private int hit(double x, double y) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            double d = Math.hypot(screenX(i) - x, screenY(i) - y);
            if (d <= radius(i) + 4 && d < bestDistance) {
                best = i;
                bestDistance = d;
            }
        }
        return best;
    }

    // ===== Малювання =====

    private void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        if (nodes.isEmpty() || positions.length != nodes.size()) {
            return;
        }

        Color text = themeTextColor();
        Color edgeColor = text.deriveColor(0, 1, 1, 0.16);
        boolean focus = selected >= 0;
        Set<Integer> selectedNeighbours = focus ? neighbours.get(selected) : Set.of();

        // Ребра: звичайні — бліді; ребра вибраного вузла — кольорові зі стрілками
        for (int[] e : edges) {
            boolean outgoing = focus && e[0] == selected;
            boolean incoming = focus && e[1] == selected;
            if (outgoing || incoming) {
                continue;   // малюються поверх, нижче
            }
            double alpha = focus || (highlightedCommunity != null && !(inCommunity(e[0]) && inCommunity(e[1])))
                    ? 0.35 : 1.0;
            g.setStroke(edgeColor.deriveColor(0, 1, 1, alpha));
            g.setLineWidth(0.8);
            g.strokeLine(screenX(e[0]), screenY(e[0]), screenX(e[1]), screenY(e[1]));
        }
        if (focus) {
            for (int[] e : edges) {
                if (e[0] == selected) {
                    drawArrow(g, e[0], e[1], OUTGOING);
                } else if (e[1] == selected) {
                    drawArrow(g, e[0], e[1], INCOMING);
                }
            }
        }

        // Вузли
        for (int i = 0; i < nodes.size(); i++) {
            boolean dim = (focus && i != selected && !selectedNeighbours.contains(i))
                    || (highlightedCommunity != null && !inCommunity(i));
            double r = radius(i);
            g.setFill(dim ? colors[i].deriveColor(0, 0.5, 1, 0.25) : colors[i]);
            g.fillOval(screenX(i) - r, screenY(i) - r, 2 * r, 2 * r);
            if (i == selected || i == hovered) {
                g.setStroke(text);
                g.setLineWidth(i == selected ? 2.5 : 1.5);
                g.strokeOval(screenX(i) - r - 2, screenY(i) - r - 2, 2 * r + 4, 2 * r + 4);
            }
        }

        // Підписи: усі (якщо ввімкнено), вибраний з сусідами і вузол під курсором
        g.setFont(Font.font(11));
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.CENTER);
        g.setFill(text);
        for (int i = 0; i < nodes.size(); i++) {
            boolean label = showLabels || i == hovered || i == selected || selectedNeighbours.contains(i);
            if (label && !(highlightedCommunity != null && !inCommunity(i) && i != hovered)) {
                g.fillText(nodes.get(i).username(), screenX(i) + radius(i) + 4, screenY(i));
            }
        }
    }

    private void drawArrow(GraphicsContext g, int from, int to, Color color) {
        double x1 = screenX(from);
        double y1 = screenY(from);
        double x2 = screenX(to);
        double y2 = screenY(to);
        double angle = Math.atan2(y2 - y1, x2 - x1);
        // Стрілка закінчується на краю кружка, а не в центрі
        double tipX = x2 - Math.cos(angle) * (radius(to) + 2);
        double tipY = y2 - Math.sin(angle) * (radius(to) + 2);

        g.setStroke(color);
        g.setLineWidth(1.6);
        g.strokeLine(x1, y1, tipX, tipY);
        double size = 7;
        g.strokeLine(tipX, tipY, tipX - size * Math.cos(angle - 0.45), tipY - size * Math.sin(angle - 0.45));
        g.strokeLine(tipX, tipY, tipX - size * Math.cos(angle + 0.45), tipY - size * Math.sin(angle + 0.45));
    }

    private boolean inCommunity(int index) {
        return highlightedCommunity != null && highlightedCommunity.equals(nodes.get(index).community());
    }

    /** Розмір вузла росте з кількістю зв'язків і трохи — з масштабом. */
    private double radius(int index) {
        double zoom = Math.max(0.8, Math.min(2.0, Math.sqrt(scale / baseScale)));
        return (3 + Math.sqrt(degree[index]) * 1.1) * zoom;
    }

    private double screenX(int index) {
        return positions[index][0] * scale + offsetX;
    }

    private double screenY(int index) {
        return positions[index][1] * scale + offsetY;
    }

    private Color themeTextColor() {
        Paint paint = textProbe.getTextFill();
        return paint instanceof Color color ? color : Color.GRAY;
    }
}