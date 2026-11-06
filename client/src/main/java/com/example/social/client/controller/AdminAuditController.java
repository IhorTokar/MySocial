package com.example.social.client.controller;

import com.example.social.client.service.AdminApiService;
import com.example.social.client.service.AdminApiService.AdminApiException;
import com.example.social.client.service.ApiClient;
import com.example.social.client.util.AdminFormat;
import com.example.social.client.util.TableColumns;
import com.example.social.shared.dto.AuditLogEntryDto;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;

import java.util.List;
import java.util.Locale;

/** Вкладка «Журнал дій»: останні дії адміністраторів з фільтром. */
public class AdminAuditController {

    private static final int LIMIT = 200;

    @FXML private TextField filterField;
    @FXML private Label statusLabel;
    @FXML private TableView<AuditLogEntryDto> auditTable;

    private final AdminApiService api = new AdminApiService(new ApiClient());
    private final ObservableList<AuditLogEntryDto> entries = FXCollections.observableArrayList();
    private final FilteredList<AuditLogEntryDto> filtered = new FilteredList<>(entries);

    @FXML
    private void initialize() {
        auditTable.getColumns().setAll(List.of(
                TableColumns.<AuditLogEntryDto>text("Час", 125, e -> AdminFormat.dateTime(e.createdAt())),
                TableColumns.<AuditLogEntryDto>text("Адмін", 100, e -> AdminFormat.orDash(e.adminUsername())),
                TableColumns.<AuditLogEntryDto>text("Дія", 150, e -> AdminFormat.action(e.action())),
                TableColumns.<AuditLogEntryDto>text("Об'єкт", 120, e -> AdminFormat.target(e.targetType(), e.targetId())),
                TableColumns.<AuditLogEntryDto>text("Деталі", 320, e -> AdminFormat.orDash(e.details()))));
        auditTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        auditTable.setPlaceholder(new Label("Записів ще немає"));
        auditTable.setItems(filtered);

        // Довгі деталі не влазять у колонку — повний текст у підказці рядка
        auditTable.setRowFactory(table -> new TableRow<AuditLogEntryDto>() {
            @Override
            protected void updateItem(AuditLogEntryDto item, boolean empty) {
                super.updateItem(item, empty);
                setTooltip(empty || item == null || item.details() == null ? null : new Tooltip(item.details()));
            }
        });

        filterField.textProperty().addListener((obs, old, text) -> {
            String query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(query.isEmpty() ? null : entry -> searchText(entry).contains(query));
            updateCount();
        });

        refresh();
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    public void refresh() {
        statusLabel.setText("Завантаження...");
        Thread.ofVirtual().start(() -> {
            try {
                List<AuditLogEntryDto> result = api.getAuditLog(LIMIT);
                Platform.runLater(() -> {
                    entries.setAll(result);
                    updateCount();
                });
            } catch (AdminApiException e) {
                Platform.runLater(() -> statusLabel.setText(e.getMessage()));
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Не вдалося з'єднатись із сервером: " + e.getMessage()));
            }
        });
    }

    private void updateCount() {
        statusLabel.setText(filtered.size() == entries.size()
                ? "Останні записи: " + entries.size()
                : "Знайдено " + filtered.size() + " із " + entries.size());
    }

    /** Усе, за чим можна фільтрувати, одним рядком у нижньому регістрі. */
    private static String searchText(AuditLogEntryDto e) {
        return String.join(" ",
                String.valueOf(e.id()),
                String.valueOf(e.adminUsername()),
                e.action(),
                AdminFormat.action(e.action()),
                AdminFormat.target(e.targetType(), e.targetId()),
                String.valueOf(e.details())).toLowerCase(Locale.ROOT);
    }
}