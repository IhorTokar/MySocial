package com.example.social.client.controller;

import com.example.social.client.service.AdminApiService;
import com.example.social.client.service.ApiClient;
import com.example.social.client.util.AdminFormat;
import com.example.social.client.util.Async;
import com.example.social.client.util.TableColumns;
import com.example.social.shared.dto.PasswordResetRequestAdminDto;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.function.LongConsumer;

/**
 * Вкладка «Заявки на пароль»: адміністратор звіряє контакт із даними акаунта,
 * встановлює тимчасовий пароль або відхиляє заявку.
 */
public class AdminPasswordRequestsController {

    private static final int MIN_PASSWORD_LENGTH = 8;
    /** Без схожих символів (0/O, 1/l/I), щоб пароль легко було продиктувати. */
    private static final String PASSWORD_ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @FXML private ComboBox<String> filterCombo;
    @FXML private Label countLabel;
    @FXML private TableView<PasswordResetRequestAdminDto> requestsTable;
    @FXML private VBox detailCard;
    @FXML private Label detailTitle;
    @FXML private Label detailInfo;
    @FXML private HBox actionsBox;
    @FXML private TextField passwordField;
    @FXML private Label statusLabel;

    private final AdminApiService api = new AdminApiService(new ApiClient());
    private LongConsumer onPendingCount = count -> { };
    private LongConsumer onOpenUser = userId -> { };

    @FXML
    private void initialize() {
        filterCombo.getItems().setAll("Нові", "Усі");
        filterCombo.setValue("Нові");
        filterCombo.valueProperty().addListener((obs, old, value) -> refresh());

        requestsTable.getColumns().setAll(List.of(
                TableColumns.<PasswordResetRequestAdminDto>text("#", 45, r -> String.valueOf(r.requestId())),
                TableColumns.<PasswordResetRequestAdminDto>text("Дата", 120, r -> AdminFormat.dateTime(r.createdAt())),
                TableColumns.<PasswordResetRequestAdminDto>text("Користувач", 140, r -> "@" + r.username()),
                TableColumns.<PasswordResetRequestAdminDto>text("Контакт із заявки", 170, r -> AdminFormat.orDash(r.contact())),
                TableColumns.<PasswordResetRequestAdminDto>text("Коментар", 200, r -> AdminFormat.orDash(r.message())),
                TableColumns.<PasswordResetRequestAdminDto>text("Стан", 110, r -> AdminFormat.requestStatus(r.status()))));
        requestsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        requestsTable.setPlaceholder(new Label("Заявок немає"));
        requestsTable.getSelectionModel().selectedItemProperty().addListener((obs, old, r) -> showDetails(r));
    }

    /** Кількість нових заявок — для назви вкладки. */
    public void setOnPendingCount(LongConsumer onPendingCount) {
        this.onPendingCount = onPendingCount;
    }

    /** «Відкрити користувача» — перехід на вкладку «Користувачі». */
    public void setOnOpenUser(LongConsumer onOpenUser) {
        this.onOpenUser = onOpenUser;
    }

    public void refreshCount() {
        Async.run(() -> {
            long count = api.countPendingPasswordRequests();
            Platform.runLater(() -> onPendingCount.accept(count));
        }, error -> { }, null);
    }

    public void refresh() {
        boolean onlyPending = "Нові".equals(filterCombo.getValue());
        Async.run(() -> {
            List<PasswordResetRequestAdminDto> requests = api.getPasswordRequests(onlyPending);
            Platform.runLater(() -> {
                requestsTable.getItems().setAll(requests);
                countLabel.setText(onlyPending ? "Нових заявок: " + requests.size() : "Усього: " + requests.size());
                if (onlyPending) {
                    onPendingCount.accept(requests.size());
                }
            });
        }, countLabel::setText, null);
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    // ===== Картка заявки =====

    private void showDetails(PasswordResetRequestAdminDto r) {
        detailCard.setVisible(r != null);
        detailCard.setManaged(r != null);
        if (r == null) {
            return;
        }
        detailTitle.setText("Заявка #" + r.requestId() + " від @" + r.username() + " · " + AdminFormat.requestStatus(r.status()));
        detailInfo.setText("Контакт із заявки: " + AdminFormat.orDash(r.contact())
                + "\nДані акаунта: email " + AdminFormat.orDash(r.email()) + " · тел. " + AdminFormat.orDash(r.phone())
                + "\nКоментар: " + AdminFormat.orDash(r.message())
                + "\nСтворено: " + AdminFormat.dateTime(r.createdAt())
                + (r.resolvedAt() != null
                ? "\nРозглянуто: " + AdminFormat.dateTime(r.resolvedAt()) + " · " + AdminFormat.orDash(r.resolvedBy())
                : ""));
        boolean pending = "pending".equals(r.status());
        actionsBox.setDisable(!pending);
        passwordField.clear();
        showStatus("", false);
    }

    @FXML
    private void handleGenerate() {
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            password.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        passwordField.setText(password.toString());
    }

    @FXML
    private void handleResolve() {
        PasswordResetRequestAdminDto r = requestsTable.getSelectionModel().getSelectedItem();
        String password = passwordField.getText() == null ? "" : passwordField.getText().trim();
        if (r == null) {
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            showStatus("Пароль має містити щонайменше " + MIN_PASSWORD_LENGTH + " символів.", true);
            return;
        }
        if (!confirm("Встановити @" + r.username() + " тимчасовий пароль? Усі сесії користувача буде завершено.")) {
            return;
        }
        actionsBox.setDisable(true);
        Async.run(() -> {
            PasswordResetRequestAdminDto updated = api.resolvePasswordRequest(r.requestId(), password);
            Platform.runLater(() -> {
                // пароль копіюється, щоб його одразу можна було надіслати користувачу
                ClipboardContent content = new ClipboardContent();
                content.putString(password);
                Clipboard.getSystemClipboard().setContent(content);
                afterAction(updated, "Пароль встановлено й скопійовано в буфер обміну. Повідомте його користувачу за контактом із заявки.");
            });
        }, error -> {
            actionsBox.setDisable(false);
            showStatus(error, true);
        }, null);
    }

    @FXML
    private void handleReject() {
        PasswordResetRequestAdminDto r = requestsTable.getSelectionModel().getSelectedItem();
        if (r == null || !confirm("Відхилити заявку #" + r.requestId() + " від @" + r.username() + "?")) {
            return;
        }
        actionsBox.setDisable(true);
        Async.run(() -> {
            PasswordResetRequestAdminDto updated = api.rejectPasswordRequest(r.requestId());
            Platform.runLater(() -> afterAction(updated, "Заявку відхилено."));
        }, error -> {
            actionsBox.setDisable(false);
            showStatus(error, true);
        }, null);
    }

    @FXML
    private void handleOpenUser() {
        PasswordResetRequestAdminDto r = requestsTable.getSelectionModel().getSelectedItem();
        if (r != null) {
            onOpenUser.accept(r.userId());
        }
    }

    /** Оновити рядок у таблиці й картку; лічильник нових заявок перерахувати. */
    private void afterAction(PasswordResetRequestAdminDto updated, String message) {
        var items = requestsTable.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).requestId() == updated.requestId()) {
                items.set(i, updated);
                requestsTable.getSelectionModel().select(i);
                break;
            }
        }
        showStatus(message, false);
        refreshCount();
    }

    private void showStatus(String text, boolean error) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll("error-label", "success-label");
        if (!text.isEmpty()) {
            statusLabel.getStyleClass().add(error ? "error-label" : "success-label");
        }
    }

    private static boolean confirm(String text) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text);
        alert.setHeaderText(null);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}