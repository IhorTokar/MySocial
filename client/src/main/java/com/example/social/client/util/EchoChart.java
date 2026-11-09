package com.example.social.client.util;

import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/** Лінійні графіки історії ехо-камер (адмін-панель і профіль): підписи осі X та підказки на точках. */
public final class EchoChart {

    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private EchoChart() {
    }

    /**
     * Підпис точки на осі X. CategoryAxis зливає однакові підписи в одну точку,
     * тому до дати, що вже траплялась (два знімки за хвилину), додається номер знімка.
     */
    public static String timeLabel(LocalDateTime time, long snapshotId, Set<String> used) {
        String label = time.format(LABEL);
        return used.add(label) ? label : label + " #" + snapshotId;
    }

    /** Точка з текстом підказки (зберігається в extraValue). */
    public static XYChart.Data<String, Number> point(String x, double y, String tooltip) {
        return new XYChart.Data<>(x, y, tooltip);
    }

    @SafeVarargs
    public static XYChart.Series<String, Number> series(String name, XYChart.Data<String, Number>... points) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(name);
        series.getData().setAll(points);
        return series;
    }

    /** Замінює дані графіка й навішує підказки: вузли точок з'являються лише після додавання на графік. */
    public static void show(LineChart<String, Number> chart, List<XYChart.Series<String, Number>> series) {
        chart.getData().clear();
        chart.getData().setAll(series);
        for (XYChart.Series<String, Number> s : series) {
            for (XYChart.Data<String, Number> d : s.getData()) {
                if (d.getNode() != null && d.getExtraValue() instanceof String text) {
                    Tooltip tooltip = new Tooltip(text);
                    tooltip.setShowDelay(Duration.millis(100));
                    Tooltip.install(d.getNode(), tooltip);
                }
            }
        }
    }
}