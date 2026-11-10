package com.example.social.client.controller;

import com.example.social.client.service.AdminApiService;
import com.example.social.client.service.AdminApiService.ScenarioStep;
import com.example.social.client.service.ApiClient;
import com.example.social.client.util.AdminFormat;
import com.example.social.client.util.Async;
import com.example.social.client.util.EchoChart;
import com.example.social.client.util.ScreenshotUtil;
import com.example.social.client.util.TableColumns;
import com.example.social.shared.dto.EchoSnapshotDetailDto;
import com.example.social.shared.dto.EchoSnapshotDto;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Вкладка «Історія ехо-камер»: графік зваженого й максимального echo-score по знімках,
 * таблиця знімків і спільноти вибраного знімка порівняно з попереднім.
 */
public class AdminEchoHistoryController {

    private static final int LIMIT = 100;
    private static final String[] SCENARIO_FILES =
            {"", "step1-baseline", "step2-echo-chamber", "step3-bridge", "step4-diversified"};

    @FXML private ComboBox<String> algorithmCombo;
    @FXML private Label summaryLabel;
    @FXML private Button snapshotButton;
    @FXML private Button scenarioButton;
    @FXML private Button resetScenarioButton;
    @FXML private LineChart<String, Number> chart;
    @FXML private TableView<EchoSnapshotDto> snapshotTable;
    @FXML private Label detailTitle;
    @FXML private TableView<EchoSnapshotDetailDto.Community> communityTable;
    @FXML private Label statusLabel;

    private final AdminApiService api = new AdminApiService(new ApiClient());
    private boolean loaded;
    /** Під час сценарію таблиця спільнот заповнюється напряму, без асинхронного дозавантаження. */
    private boolean scenarioRunning;

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
            if (snapshot != null && !scenarioRunning) {
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
            Platform.runLater(() -> showDetail(detail));
        }, statusLabel::setText, null);
    }

    private void showDetail(EchoSnapshotDetailDto detail) {
        long snapshotId = detail.snapshot().snapshotId();
        communityTable.getItems().setAll(detail.communities());
        detailTitle.setText(detail.previousSnapshotId() == null
                ? "Спільноти знімка #" + snapshotId + " (перший — порівнювати нема з чим)"
                : "Спільноти знімка #" + snapshotId + " порівняно з #" + detail.previousSnapshotId());
    }

    // ===== Сценарій для звіту =====

    /**
     * Кроки 1–4 сценарію на сервері по черзі; після кожного — оновлення вкладки і PNG вікна
     * в ~/Pictures/MySocial/scenario_<дата>/. Спільнота групи A виділяється в таблиці.
     */
    @FXML
    private void handleRunScenario() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Запустити сценарій формування й розмикання ехо-камери?\n\n"
                        + "• буде створено тестові групи echo_a1…a8 та echo_b1…b8 (попередні, якщо є, видаляться);\n"
                        + "• 4 кроки, після кожного — знімок ехо-камер і PNG цього вікна;\n"
                        + "• триває 1–3 хвилини; NLP-сервіс і Neo4j мають бути запущені.\n\n"
                        + "Порада: розгорніть вікно на весь екран — рисунки будуть більшими.");
        confirm.setHeaderText(null);
        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) {
            return;
        }

        algorithmCombo.setValue("Louvain");   // сценарій і профіль користувача використовують Louvain
        setScenarioRunning(true);
        Scene scene = chart.getScene();

        Async.run(() -> {
            Path dir = ScreenshotUtil.newSeriesDir("scenario");
            onFx(() -> statusLabel.setText("Прибираю попередні тестові групи..."));
            api.resetScenario();

            for (int step = 1; step <= 4; step++) {
                int current = step;
                onFx(() -> statusLabel.setText("Крок " + current + " з 4 виконується на сервері..."));
                ScenarioStep result = api.runScenarioStep(step);
                List<EchoSnapshotDto> history = api.getEchoHistory(false, LIMIT);
                EchoSnapshotDetailDto detail = api.getEchoSnapshot(result.snapshot().snapshotId());

                onFx(() -> {
                    show(history, result.snapshot().snapshotId());
                    showDetail(detail);
                    highlightCommunity(result.groupA() == null ? null : result.groupA().community());
                    statusLabel.setText(caption(result));
                });
                Thread.sleep(700);   // дати інтерфейсу перемалювати графік і таблиці
                onFx(() -> ScreenshotUtil.save(scene, dir.resolve(SCENARIO_FILES[current] + ".png")));
            }
            onFx(() -> statusLabel.setText("Сценарій завершено. 4 рисунки збережено в " + dir
                    + ". Тестові групи лишились — можна зняти профіль echo_a1 і вкладку «Граф»."));
        }, statusLabel::setText, () -> setScenarioRunning(false));
    }

    @FXML
    private void handleResetScenario() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Видалити тестові групи echo_a1…a8 та echo_b1…b8 разом з їхніми постами й підписками?\n"
                        + "Знімки історії лишаться.");
        confirm.setHeaderText(null);
        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) {
            return;
        }
        Async.run(() -> {
            api.resetScenario();
            Platform.runLater(() -> statusLabel.setText("Тестові групи видалено."));
        }, statusLabel::setText, null);
    }

    /** Підпис, що потрапляє на рисунок: який крок і як змінилась спільнота групи A. */
    private static String caption(ScenarioStep result) {
        String text = "Крок " + result.step() + " «" + result.title() + "», знімок #" + result.snapshot().snapshotId();
        if (result.groupA() != null && result.groupA().community() != null) {
            text += String.format(Locale.ROOT, " · спільнота групи A #%d (%d учасн.): echo %.3f — виділено в таблиці",
                    result.groupA().community(), result.groupA().members(), result.groupA().echoChamberScore());
        }
        return text;
    }

    private void highlightCommunity(Long community) {
        if (community == null) {
            return;
        }
        communityTable.getItems().stream()
                .filter(c -> c.community() == community)
                .findFirst()
                .ifPresent(c -> {
                    communityTable.getSelectionModel().select(c);
                    communityTable.scrollTo(c);
                });
    }

    private void setScenarioRunning(boolean running) {
        scenarioRunning = running;
        scenarioButton.setDisable(running);
        resetScenarioButton.setDisable(running);
        snapshotButton.setDisable(running);
        algorithmCombo.setDisable(running);
    }

    @FunctionalInterface
    private interface FxAction {
        void run() throws Exception;
    }

    /** Виконати дію в потоці інтерфейсу й дочекатися її завершення (виклик із фонового потоку). */
    private static void onFx(FxAction action) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                action.run();
                done.complete(null);
            } catch (Exception e) {
                done.completeExceptionally(e);
            }
        });
        try {
            done.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
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