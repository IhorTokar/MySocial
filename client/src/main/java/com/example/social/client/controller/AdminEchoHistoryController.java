package com.example.social.client.controller;

import com.example.social.client.service.AdminApiService;
import com.example.social.client.service.ApiClient;
import com.example.social.client.util.AdminFormat;
import com.example.social.client.util.Async;
import com.example.social.client.util.EchoChart;
import com.example.social.client.util.TableColumns;
import com.example.social.shared.dto.EchoSnapshotDetailDto;
import com.example.social.shared.dto.EchoSnapshotDto;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Вкладка «Історія ехо-камер»: графік зваженого й максимального echo-score по знімках,
 * таблиця знімків і спільноти вибраного знімка порівняно з попереднім.
 */
public class AdminEchoHistoryController {

    private static final int LIMIT = 100;

    @FXML private ComboBox<String> algorithmCombo;
    @FXML private Label summaryLabel;
    @FXML private Button snapshotButton;
    @FXML private LineChart<String, Number> chart;
    @FXML private TableView<EchoSnapshotDto> snapshotTable;
    @FXML private Label detailTitle;
    @FXML private TableView<EchoSnapshotDetailDto.Community> communityTable;
    @FXML private Label statusLabel;

    private final AdminApiService api = new AdminApiService(new ApiClient());
    private boolean loaded;

    @FXML
    private void initialize() {
        algorithmCombo.getItems().setAll("Louvain", "Leiden");
        algorithmCombo.setValue("Louvain");
        algorithmCombo.valueProperty().addListener((obs, old, value) -> refresh(null));

        snapshotTable.getColumns().setAll(List.of(
                TableColumns.<EchoSnapshotDto>text("#", 45, s -> String.valueOf(s.snapshotId())),
                TableColumns.<EchoSnapshotDto>text("Час", 120, s -> AdminFormat.dateTime(s.createdAt())),
                TableColumns.<EchoSnapshotDto>text("Хто", 90, s -> s.createdBy() == null ? "розклад" : s.createdBy()),
                TableColumns.<EchoSnapshotDto>text("Спільнот", 70, s -> String.valueOf(s.communityCount())),
                TableColumns.<EchoSnapshotDto>text("Учасників", 80, s -> String.valueOf(s.userCount())),
                TableColumns.<EchoSnapshotDto>text("Echo (зваж.)", 95, s -> AdminFormat.score(s.weightedEchoScore())),
                TableColumns.<EchoSnapshotDto>text("Макс.", 70, s -> AdminFormat.score(s.maxEchoScore()))));
        snapshotTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        snapshotTable.setPlaceholder(new Label("Знімків ще немає — натисніть «Зробити знімок»"));
        snapshotTable.getSelectionModel().selectedItemProperty().addListener((obs, old, snapshot) -> {
            if (snapshot != null) {
                loadDetail(snapshot.snapshotId());
            }
        });

        communityTable.getColumns().setAll(List.of(
                TableColumns.<EchoSnapshotDetailDto.Community>text("Спільнота", 80, c -> "#" + c.community()),
                TableColumns.<EchoSnapshotDetailDto.Community>text("Учасників", 75, c -> String.valueOf(c.memberCount())),
                TableColumns.<EchoSnapshotDetailDto.Community>text("Постів", 60, c -> String.valueOf(c.postCount())),
                TableColumns.<EchoSnapshotDetailDto.Community>text("Echo", 65,
                        c -> c.postCount() == 0 ? "немає постів" : AdminFormat.score(c.echoChamberScore())),
                TableColumns.<EchoSnapshotDetailDto.Community>text("Раніше", 75,
                        c -> c.previousCommunity() == null ? "нова" : "#" + c.previousCommunity()),
                TableColumns.<EchoSnapshotDetailDto.Community>text("Збіг складу", 85, c -> AdminFormat.percent(c.overlap())),
                TableColumns.<EchoSnapshotDetailDto.Community>text("Δ echo", 70, c -> AdminFormat.delta(c.scoreDelta()))));
        communityTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        communityTable.setPlaceholder(new Label("Виберіть знімок зліва"));
    }

    /** Дані завантажуються при першому відкритті вкладки. */
    public void onShown() {
        if (!loaded) {
            loaded = true;
            refresh(null);
        }
    }

    @FXML
    private void handleRefresh() {
        refresh(null);
    }

    @FXML
    private void handleSnapshot() {
        boolean leiden = isLeiden();
        snapshotButton.setDisable(true);
        statusLabel.setText("Синхронізую граф, шукаю спільноти й рахую показники — це може зайняти кілька секунд...");
        Async.run(() -> {
            EchoSnapshotDto snapshot = api.takeEchoSnapshot(leiden);
            Platform.runLater(() -> {
                statusLabel.setText(String.format(Locale.ROOT, "Знімок #%d збережено: спільнот — %d, зважений echo-score %.3f.",
                        snapshot.snapshotId(), snapshot.communityCount(), snapshot.weightedEchoScore()));
                refresh(snapshot.snapshotId());
            });
        }, statusLabel::setText, () -> snapshotButton.setDisable(false));
    }

    // ===== Завантаження =====

    /** @param selectId який знімок вибрати після завантаження; null — найновіший */
    private void refresh(Long selectId) {
        boolean leiden = isLeiden();
        Async.run(() -> {
            List<EchoSnapshotDto> history = api.getEchoHistory(leiden, LIMIT);
            Platform.runLater(() -> show(history, selectId));
        }, statusLabel::setText, null);
    }

    private void show(List<EchoSnapshotDto> history, Long selectId) {
        // графік — від старого до нового, таблиця — найновіші зверху
        Set<String> usedLabels = new HashSet<>();
        XYChart.Series<String, Number> weighted = EchoChart.series("Зважений echo-score мережі");
        XYChart.Series<String, Number> max = EchoChart.series("Найвищий echo-score спільноти");
        for (EchoSnapshotDto s : history) {
            String x = EchoChart.timeLabel(s.createdAt(), s.snapshotId(), usedLabels);
            String tooltip = String.format(Locale.ROOT, "Знімок #%d · %s%nспільнот: %d, учасників: %d%nзважений: %.3f, максимум: %.3f",
                    s.snapshotId(), AdminFormat.dateTime(s.createdAt()), s.communityCount(), s.userCount(),
                    s.weightedEchoScore(), s.maxEchoScore());
            weighted.getData().add(EchoChart.point(x, s.weightedEchoScore(), tooltip));
            max.getData().add(EchoChart.point(x, s.maxEchoScore(), tooltip));
        }
        EchoChart.show(chart, List.of(weighted, max));

        List<EchoSnapshotDto> newestFirst = new ArrayList<>(history);
        Collections.reverse(newestFirst);
        snapshotTable.getItems().setAll(newestFirst);
        communityTable.getItems().clear();
        detailTitle.setText("Спільноти знімка");

        summaryLabel.setText(summary(history));

        EchoSnapshotDto toSelect = newestFirst.stream()
                .filter(s -> selectId == null || s.snapshotId() == selectId)
                .findFirst().orElse(null);
        if (toSelect != null) {
            snapshotTable.getSelectionModel().select(toSelect);
            snapshotTable.scrollTo(toSelect);
        }
    }

    private void loadDetail(long snapshotId) {
        Async.run(() -> {
            EchoSnapshotDetailDto detail = api.getEchoSnapshot(snapshotId);
            Platform.runLater(() -> {
                communityTable.getItems().setAll(detail.communities());
                detailTitle.setText(detail.previousSnapshotId() == null
                        ? "Спільноти знімка #" + snapshotId + " (перший — порівнювати нема з чим)"
                        : "Спільноти знімка #" + snapshotId + " порівняно з #" + detail.previousSnapshotId());
            });
        }, statusLabel::setText, null);
    }

    /** «Знімків: 5 · 0.455 → 0.418 (−0.037)» — зміна від першого знімка до останнього. */
    private static String summary(List<EchoSnapshotDto> history) {
        if (history.isEmpty()) {
            return "Знімків ще немає";
        }
        EchoSnapshotDto first = history.get(0);
        EchoSnapshotDto last = history.get(history.size() - 1);
        if (history.size() == 1) {
            return "Знімків: 1 · echo " + AdminFormat.score(last.weightedEchoScore());
        }
        return "Знімків: " + history.size() + " · echo " + AdminFormat.score(first.weightedEchoScore())
                + " → " + AdminFormat.score(last.weightedEchoScore())
                + " (" + AdminFormat.delta(last.weightedEchoScore() - first.weightedEchoScore()) + ")";
    }

    private boolean isLeiden() {
        return "Leiden".equals(algorithmCombo.getValue());
    }
}