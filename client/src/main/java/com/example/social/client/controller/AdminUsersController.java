package com.example.social.client.controller;

import com.example.social.client.service.AdminApiService;
import com.example.social.client.service.ApiClient;
import com.example.social.client.util.AdminFormat;
import com.example.social.client.util.Async;
import com.example.social.client.util.AvatarUtil;
import com.example.social.client.util.SessionManager;
import com.example.social.client.util.TableColumns;
import com.example.social.shared.dto.AdminProfileUpdateDto;
import com.example.social.shared.dto.AdminUserDto;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Вкладка «Користувачі»: пошук за ID / uid / іменем і дії над вибраним користувачем. */
public class AdminUsersController {

    private static final int MIN_PASSWORD_LENGTH = 8;

    @FXML private TextField searchField;
    @FXML private Label searchStatusLabel;
    @FXML private TableView<AdminUserDto> usersTable;

    @FXML private VBox detailCard;
    @FXML private StackPane avatarHolder;
    @FXML private Label nameLabel;
    @FXML private Label handleLabel;
    @FXML private Label infoLabel;
    @FXML private Label blockedLabel;
    @FXML private TextField blockReasonField;
    @FXML private Button blockButton;
    @FXML private ComboBox<String> roleCombo;
    @FXML private Button roleButton;
    @FXML private TextField displayNameField;
    @FXML private TextArea aboutMeField;
    @FXML private CheckBox removeAvatarCheck;
    @FXML private PasswordField newPasswordField;
    @FXML private Label statusLabel;
    @FXML private CheckBox removeCoverCheck;

    private final AdminApiService api = new AdminApiService(new ApiClient());
    private MainShellController shell;
    private AdminUserDto selected;

    @FXML
    private void initialize() {
        roleCombo.getItems().setAll("user", "admin", "guest");

        usersTable.getColumns().setAll(List.of(
                TableColumns.<AdminUserDto>text("ID", 55, u -> String.valueOf(u.userId())),
                TableColumns.<AdminUserDto>text("Ім'я користувача", 170, AdminUserDto::username),
                TableColumns.<AdminUserDto>text("Відображуване ім'я", 150, u -> AdminFormat.orDash(u.displayName())),
                TableColumns.<AdminUserDto>text("Роль", 65, AdminUserDto::role),
                TableColumns.<AdminUserDto>text("Статус", 105, u -> u.blocked() ? "заблоковано" : "активний"),
                TableColumns.<AdminUserDto>text("Пости", 60, u -> String.valueOf(u.postsCount())),
                TableColumns.<AdminUserDto>text("Підписники", 90, u -> String.valueOf(u.followersCount()))));
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        usersTable.setPlaceholder(new Label("Введіть ID, uid або частину імені й натисніть «Знайти»"));
        usersTable.getSelectionModel().selectedItemProperty().addListener((obs, old, user) -> showDetails(user));
    }

    public void setShell(MainShellController shell) {
        this.shell = shell;
    }

    /** Відкриває користувача за ID — викликається з вкладки «Контент» і з графа. */
    public void showUser(long userId) {
        searchField.setText(String.valueOf(userId));
        search(String.valueOf(userId), userId);
    }

    // ===== Пошук =====

    @FXML
    private void handleSearch() {
        search(searchField.getText(), null);
    }

    private void search(String query, Long selectId) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            searchStatusLabel.setText("Введіть ID, uid або частину імені");
            return;
        }
        searchStatusLabel.setText("Пошук...");
        Async.run(() -> {
            List<AdminUserDto> found = api.searchUsers(q);
            Platform.runLater(() -> {
                usersTable.getItems().setAll(found);
                searchStatusLabel.setText(found.isEmpty() ? "Нікого не знайдено" : "Знайдено: " + found.size());
                // Один результат або конкретний ID — одразу відкриваємо картку
                found.stream()
                        .filter(u -> selectId == null ? found.size() == 1 : u.userId() == selectId)
                        .findFirst()
                        .ifPresent(u -> usersTable.getSelectionModel().select(u));
            });
        }, searchStatusLabel::setText, null);
    }

    // ===== Картка користувача =====

    private void showDetails(AdminUserDto user) {
        selected = user;
        detailCard.setVisible(user != null);
        detailCard.setManaged(user != null);
        if (user == null) {
            return;
        }

        String name = user.displayName() != null ? user.displayName() : user.username();
        avatarHolder.getChildren().setAll(AvatarUtil.create(name, user.avatarUrl(), 56));
        nameLabel.setText(name);
        handleLabel.setText("@" + user.username());
        infoLabel.setText("ID " + user.userId() + " · uid " + user.uid() + " · " + user.email()
                + (user.phone() != null ? " · тел. " + user.phone() : "")
                + "\nРоль: " + user.role() + " · зареєстрований " + AdminFormat.dateTime(user.createdAt())
                + "\nПостів: " + user.postsCount() + " · підписників: " + user.followersCount()
                + " · підписок: " + user.followingCount());

        boolean blocked = user.blocked();
        blockedLabel.setText(blocked
                ? "Заблоковано " + AdminFormat.dateTime(user.blockedAt())
                + (user.blockedReason() != null ? ": " + user.blockedReason() : "")
                : "");
        blockedLabel.setVisible(blocked);
        blockedLabel.setManaged(blocked);

        blockButton.setText(blocked ? "Розблокувати" : "Заблокувати");
        blockButton.getStyleClass().removeAll("button-danger", "button-secondary");
        blockButton.getStyleClass().add(blocked ? "button-secondary" : "button-danger");
        blockReasonField.clear();
        blockReasonField.setDisable(blocked);

        roleCombo.setValue(user.role());
        displayNameField.setText(user.displayName() == null ? "" : user.displayName());
        aboutMeField.setText(user.aboutMe() == null ? "" : user.aboutMe());
        removeAvatarCheck.setSelected(false);
        removeAvatarCheck.setDisable(user.avatarUrl() == null);
        removeCoverCheck.setSelected(false);
        removeCoverCheck.setDisable(user.coverUrl() == null);
        newPasswordField.clear();

        // Власний акаунт: блокування й зміну ролі сервер однаково не дозволить
        boolean self = Objects.equals(user.userId(), SessionManager.getInstance().getUserId());
        blockButton.setDisable(self);
        blockReasonField.setDisable(self || blocked);
        roleButton.setDisable(self);
        showStatus(self ? "Це ваш акаунт: блокування й зміна ролі недоступні." : "", false);
    }

    // ===== Дії =====

    @FXML
    private void handleOpenProfile() {
        if (selected != null && shell != null) {
            shell.showProfile(selected.userId());
        }
    }

    @FXML
    private void handleBlockToggle() {
        AdminUserDto user = selected;
        if (user == null) {
            return;
        }
        if (user.blocked()) {
            runAction("Користувача розблоковано.", () -> api.unblockUser(user.userId()));
            return;
        }
        if (!confirm("Заблокувати @" + user.username() + "? Користувач не зможе увійти, а відкриті сесії й чат "
                + "будуть завершені.")) {
            return;
        }
        String reason = blankToNull(blockReasonField.getText());
        runAction("Користувача заблоковано.", () -> api.blockUser(user.userId(), reason));
    }

    @FXML
    private void handleChangeRole() {
        AdminUserDto user = selected;
        String role = roleCombo.getValue();
        if (user == null || role == null) {
            return;
        }
        if (role.equals(user.role())) {
            showStatus("Роль не змінилась.", false);
            return;
        }
        if ("admin".equals(role) && !confirm("Надати @" + user.username() + " права адміністратора?")) {
            return;
        }
        runAction("Роль змінено: " + user.role() + " → " + role + ".", () -> api.changeRole(user.userId(), role));
    }

    @FXML
    private void handleSaveProfile() {
        AdminUserDto user = selected;
        if (user == null) {
            return;
        }
        AdminProfileUpdateDto request = new AdminProfileUpdateDto(
                displayNameField.getText(), aboutMeField.getText(),
                removeAvatarCheck.isSelected(), removeCoverCheck.isSelected());
        runAction("Профіль збережено.", () -> api.updateProfile(user.userId(), request));
    }

    @FXML
    private void handleResetPassword() {
        AdminUserDto user = selected;
        String password = newPasswordField.getText();
        if (user == null) {
            return;
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            showStatus("Пароль має містити щонайменше " + MIN_PASSWORD_LENGTH + " символів.", true);
            return;
        }
        if (!confirm("Скинути пароль @" + user.username() + "? Користувач вийде з усіх пристроїв.")) {
            return;
        }
        setBusy(true);
        Async.run(() -> {
            api.resetPassword(user.userId(), password);
            Platform.runLater(() -> {
                newPasswordField.clear();
                showStatus("Пароль скинуто, усі сесії користувача завершено.", false);
            });
        }, error -> showStatus(error, true), () -> setBusy(false));
    }

    // ===== Допоміжне =====

    @FunctionalInterface
    private interface UserCall {
        AdminUserDto run() throws Exception;
    }

    /** Дія, що повертає оновленого користувача: рядок у таблиці й картка оновлюються. */
    private void runAction(String successText, UserCall call) {
        setBusy(true);
        showStatus("Виконується...", false);
        Async.run(() -> {
            AdminUserDto updated = call.run();
            Platform.runLater(() -> {
                replaceInTable(updated);
                showDetails(updated);
                showStatus(successText, false);
            });
        }, error -> showStatus(error, true), () -> setBusy(false));
    }

    private void replaceInTable(AdminUserDto updated) {
        ObservableList<AdminUserDto> items = usersTable.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).userId() == updated.userId()) {
                items.set(i, updated);
                usersTable.getSelectionModel().select(i);
                return;
            }
        }
        items.add(0, updated);
        usersTable.getSelectionModel().select(0);
    }

    /** Поки йде запит, картка недоступна — щоб не натиснути дві дії одночасно. */
    private void setBusy(boolean busy) {
        detailCard.setDisable(busy);
    }

    private void showStatus(String text, boolean error) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll("secondary-label", "error-label", "success-label");
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

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}