package com.example.social.client.controller;

import com.example.social.client.service.AdminApiService;
import com.example.social.client.service.AdminApiService.AdminApiException;
import com.example.social.client.service.ApiClient;
import com.example.social.client.util.AdminFormat;
import com.example.social.client.util.TableColumns;
import com.example.social.shared.dto.RecommendationWeightsDto;
import com.example.social.shared.dto.WeightsUpdateRequestDto;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Вкладка «Ваги стрічки»: редагування чинних ваг та історія змін з відновленням. */
public class AdminWeightsController {

    private static final double SUM_TOLERANCE = 1e-3;   // як на сервері
    private static final int HISTORY_LIMIT = 50;

    @FXML private Label currentVersionLabel;
    @FXML private GridPane weightsGrid;
    @FXML private Label sumLabel;
    @FXML private Button normalizeButton;
    @FXML private TextField commentField;
    @FXML private Button saveButton;
    @FXML private Button resetButton;
    @FXML private Label statusLabel;
    @FXML private TableView<RecommendationWeightsDto> historyTable;
    @FXML private Button restoreButton;

    private final AdminApiService api = new AdminApiService(new ApiClient());

    /** Рядки редактора: 0–4 — основні ваги (сума = 1), 5 — бонус за підписку. */
    private final List<WeightRow> rows = new ArrayList<>();
    private RecommendationWeightsDto loaded;
    private boolean busy;

    @FXML
    private void initialize() {
        rows.add(new WeightRow("Популярність (P)", "Лайки за останні 7 днів", 0));
        rows.add(new WeightRow("Item-CF (I)", "Пости, які оцінили люди зі схожими реакціями", 1));
        rows.add(new WeightRow("Графова подібність (G)", "Реакції користувачів, близьких у графі (Node2Vec)", 2));
        rows.add(new WeightRow("Семантика (S)", "Близькість тексту до постів, які сподобались", 3));
        rows.add(new WeightRow("Свіжість (R)", "Новіші публікації вище", 4));
        rows.add(new WeightRow("Бонус за підписку", "Додається, якщо автор у підписках; у суму не входить", 5));

        setupHistoryTable();
        reload();
    }

    // ===== Обробники кнопок =====

    @FXML
    private void handleSave() {
        WeightsUpdateRequestDto request = new WeightsUpdateRequestDto(
                rows.get(0).get(), rows.get(1).get(), rows.get(2).get(), rows.get(3).get(), rows.get(4).get(),
                rows.get(5).get(), blankToNull(commentField.getText()));

        runAsync("Збереження...", () -> {
            RecommendationWeightsDto saved = api.updateWeights(request);
            Platform.runLater(() -> {
                commentField.clear();
                reload();
                showStatus("Збережено версію #" + saved.id() + ". Стрічка вже рахується з новими вагами.", false);
            });
        });
    }

    @FXML
    private void handleReset() {
        if (loaded != null) {
            fillEditor(loaded);
        }
        showStatus("", false);
    }

    /** Ділить п'ять основних ваг на їхню суму, щоб сума стала 1. */
    @FXML
    private void handleNormalize() {
        double sum = mainSum();
        if (sum <= 0) {
            showStatus("Усі основні ваги нульові — нормалізувати нічого", true);
            return;
        }
        for (int i = 0; i < 5; i++) {
            rows.get(i).set(rows.get(i).get() / sum);
        }
    }

    @FXML
    private void handleRestore() {
        RecommendationWeightsDto selected = historyTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showStatus("Виберіть версію в таблиці історії", true);
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Відновити ваги з версії #" + selected.id() + "? Буде створено нову версію з цими значеннями.");
        confirm.setHeaderText(null);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        runAsync("Відновлення...", () -> {
            RecommendationWeightsDto restored = api.restoreWeights(selected.id());
            Platform.runLater(() -> {
                reload();
                showStatus("Відновлено: створено версію #" + restored.id() + " зі значеннями версії #"
                        + selected.id() + ".", false);
            });
        });
    }

    @FXML
    private void handleRefresh() {
        reload();
    }

    // ===== Завантаження й відображення =====

    private void reload() {
        runAsync("Завантаження...", () -> {
            RecommendationWeightsDto current = api.getWeights();
            List<RecommendationWeightsDto> history = api.getWeightsHistory(HISTORY_LIMIT);
            Platform.runLater(() -> {
                loaded = current;
                fillEditor(current);
                currentVersionLabel.setText(current.id() == null
                        ? "Зараз діють значення з application.yml — у базі ще немає жодної версії."
                        : "Чинна версія #" + current.id() + " від " + AdminFormat.dateTime(current.createdAt())
                        + ", автор: " + AdminFormat.orDash(current.createdBy())
                        + (current.comment() != null ? " — «" + current.comment() + "»" : ""));
                historyTable.getItems().setAll(history);
                showStatus("", false);
            });
        });
    }

    private void fillEditor(RecommendationWeightsDto w) {
        rows.get(0).set(w.popularity());
        rows.get(1).set(w.itemCf());
        rows.get(2).set(w.graphSim());
        rows.get(3).set(w.semantic());
        rows.get(4).set(w.recency());
        rows.get(5).set(w.followedBonus());
        updateState();
    }

    private void setupHistoryTable() {
        historyTable.getColumns().setAll(List.of(
                TableColumns.<RecommendationWeightsDto>text("#", 45, w -> String.valueOf(w.id())),
                TableColumns.<RecommendationWeightsDto>text("Дата", 125, w -> AdminFormat.dateTime(w.createdAt())),
                TableColumns.<RecommendationWeightsDto>text("Автор", 95, w -> AdminFormat.orDash(w.createdBy())),
                TableColumns.<RecommendationWeightsDto>text("P", 55, w -> AdminFormat.weight(w.popularity())),
                TableColumns.<RecommendationWeightsDto>text("I", 55, w -> AdminFormat.weight(w.itemCf())),
                TableColumns.<RecommendationWeightsDto>text("G", 55, w -> AdminFormat.weight(w.graphSim())),
                TableColumns.<RecommendationWeightsDto>text("S", 55, w -> AdminFormat.weight(w.semantic())),
                TableColumns.<RecommendationWeightsDto>text("R", 55, w -> AdminFormat.weight(w.recency())),
                TableColumns.<RecommendationWeightsDto>text("Бонус", 60, w -> AdminFormat.weight(w.followedBonus())),
                TableColumns.<RecommendationWeightsDto>text("Коментар", 200, w -> AdminFormat.orDash(w.comment()))));
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        historyTable.setPlaceholder(new Label("Змін ще не було — діють значення з application.yml"));

        // Подвійний клік підставляє значення версії в редактор (без збереження)
        historyTable.setRowFactory(table -> {
            TableRow<RecommendationWeightsDto> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    fillEditor(row.getItem());
                    showStatus("Значення версії #" + row.getItem().id()
                            + " підставлено в редактор. Натисніть «Зберегти нову версію», щоб застосувати.", false);
                }
            });
            return row;
        });
    }

    /** Сума, кнопки і підсвітка після будь-якої зміни значень. */
    private void updateState() {
        double sum = mainSum();
        boolean valid = Math.abs(sum - 1.0) <= SUM_TOLERANCE;
        boolean dirty = loaded != null && isDirty();

        sumLabel.setText("Сума п'яти основних ваг: " + AdminFormat.weight(sum) + (valid ? " ✓" : " — має бути 1"));
        sumLabel.getStyleClass().removeAll("success-label", "error-label");
        sumLabel.getStyleClass().add(valid ? "success-label" : "error-label");

        saveButton.setDisable(busy || !valid || !dirty);
        resetButton.setDisable(busy || !dirty);
        normalizeButton.setDisable(busy || valid);
        restoreButton.setDisable(busy);
    }

    private boolean isDirty() {
        double[] original = {loaded.popularity(), loaded.itemCf(), loaded.graphSim(),
                loaded.semantic(), loaded.recency(), loaded.followedBonus()};
        for (int i = 0; i < original.length; i++) {
            if (Math.abs(rows.get(i).get() - original[i]) > 1e-9) {
                return true;
            }
        }
        return false;
    }

    private double mainSum() {
        double sum = 0;
        for (int i = 0; i < 5; i++) {
            sum += rows.get(i).get();
        }
        return sum;
    }

    // ===== Асинхронні запити =====

    @FunctionalInterface
    private interface ApiCall {
        void run() throws Exception;
    }

    /** Запит у віртуальному потоці; помилки сервера показуються в statusLabel. */
    private void runAsync(String progressText, ApiCall call) {
        setBusy(true);
        showStatus(progressText, false);
        Thread.ofVirtual().start(() -> {
            try {
                call.run();
                Platform.runLater(() -> setBusy(false));
            } catch (AdminApiException e) {
                Platform.runLater(() -> {
                    setBusy(false);
                    showStatus(e.getMessage(), true);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setBusy(false);
                    showStatus("Не вдалося з'єднатись із сервером: " + e.getMessage(), true);
                });
            }
        });
    }

    private void setBusy(boolean value) {
        busy = value;
        updateState();
    }

    private void showStatus(String text, boolean error) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll("secondary-label", "error-label");
        if (!text.isEmpty()) {
            statusLabel.getStyleClass().add(error ? "error-label" : "secondary-label");
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    // ===== Рядок редактора: назва, повзунок 0..1, поле з точним значенням =====

    private final class WeightRow {
        private final Slider slider = new Slider(0, 1, 0);
        private final TextField field = new TextField();
        private boolean syncing;

        WeightRow(String title, String hint, int rowIndex) {
            Label titleLabel = new Label(title);
            Label hintLabel = new Label(hint);
            hintLabel.getStyleClass().add("secondary-label");
            VBox nameBox = new VBox(2, titleLabel, hintLabel);
            nameBox.setMinWidth(260);

            slider.setBlockIncrement(0.01);
            GridPane.setHgrow(slider, Priority.ALWAYS);

            field.getStyleClass().add("text-field");
            field.setPrefColumnCount(5);
            field.setMaxWidth(90);

            slider.valueProperty().addListener((obs, old, value) -> {
                if (!syncing) {
                    field.setText(AdminFormat.weight(round3(value.doubleValue())));
                }
                updateState();
            });
            field.setOnAction(e -> applyField());
            field.focusedProperty().addListener((obs, was, focused) -> {
                if (!focused) {
                    applyField();
                }
            });

            weightsGrid.addRow(rowIndex, nameBox, slider, field);
        }

        /** Значення з поля вводу (кома теж приймається), обмежене діапазоном 0..1. */
        private void applyField() {
            try {
                double value = Double.parseDouble(field.getText().trim().replace(',', '.'));
                set(Math.max(0, Math.min(1, value)));
            } catch (NumberFormatException e) {
                field.setText(AdminFormat.weight(get()));
            }
        }

        /** Значення, округлене до тисячних: саме воно відправляється на сервер. */
        double get() {
            return round3(slider.getValue());
        }

        void set(double value) {
            syncing = true;
            slider.setValue(round3(value));
            syncing = false;
            field.setText(AdminFormat.weight(round3(value)));
        }
    }
}