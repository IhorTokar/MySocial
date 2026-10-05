package com.example.social.client.controller;

import com.example.social.client.service.ApiClient;
import com.example.social.client.service.ChatConnection;
import com.example.social.client.service.UnreadCounter;
import com.example.social.client.service.UserApiService;
import com.example.social.client.util.AppSettings;
import com.example.social.client.util.SceneUtil;
import com.example.social.client.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import com.example.social.client.util.ThemeManager;
import com.example.social.client.util.ThemeManager.Theme;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.util.Optional;

public class SettingsController {

    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button changePasswordButton;
    @FXML private Label passwordStatusLabel;

    @FXML private PasswordField deleteConfirmPasswordField;
    @FXML private Button deleteAccountButton;
    @FXML private Label deleteStatusLabel;

    @FXML private Slider cardWidthSlider;
    @FXML private TextField cardWidthField;
    @FXML private Slider imageHeightSlider;
    @FXML private TextField imageHeightField;
    @FXML private Button resetSizesButton;
    @FXML private HBox presetButtonsBox;
    @FXML private ToggleGroup themeToggleGroup;
    @FXML private RadioButton lightThemeRadio;
    @FXML private RadioButton darkThemeRadio;
    @FXML private RadioButton customThemeRadio;
    @FXML private VBox customColorsBox;
    @FXML private ColorPicker customBgPicker;
    @FXML private ColorPicker customAccentPicker;
    @FXML private ColorPicker customTextPicker;

    private final AppSettings appSettings = AppSettings.getInstance();
    private boolean suppressSizeSync;
    private final ThemeManager themeManager = ThemeManager.getInstance();
    private boolean suppressThemeSync;

    private final UserApiService userApiService = new UserApiService(new ApiClient());

    @FXML
    private void initialize() {
        bindSizeControls();
        bindThemeControls();
    }

    private void bindSizeControls() {
        cardWidthSlider.setMin(AppSettings.MIN_CARD_WIDTH);
        cardWidthSlider.setMax(AppSettings.MAX_CARD_WIDTH);
        cardWidthSlider.setValue(appSettings.cardWidthProperty().get());

        imageHeightSlider.setMin(AppSettings.MIN_IMAGE_HEIGHT);
        imageHeightSlider.setMax(AppSettings.MAX_IMAGE_HEIGHT);
        imageHeightSlider.setValue(appSettings.imageHeightProperty().get());

        cardWidthField.setText(formatSize(appSettings.cardWidthProperty().get()));
        imageHeightField.setText(formatSize(appSettings.imageHeightProperty().get()));

        cardWidthSlider.valueProperty().addListener((obs, old, value) -> {
            if (suppressSizeSync) return;
            applyCardWidth(value.doubleValue());
            cardWidthField.setText(formatSize(value.doubleValue()));
            highlightActivePreset();
        });

        imageHeightSlider.valueProperty().addListener((obs, old, value) -> {
            if (suppressSizeSync) return;
            applyImageHeight(value.doubleValue());
            imageHeightField.setText(formatSize(value.doubleValue()));
            highlightActivePreset();
        });

        cardWidthField.setOnAction(e -> parseAndApply(cardWidthField.getText(), this::applyCardWidth, cardWidthSlider));
        imageHeightField.setOnAction(e -> parseAndApply(imageHeightField.getText(), this::applyImageHeight, imageHeightSlider));
        buildPresetButtons();
    }

    private void applyCardWidth(double value) {
        appSettings.setCardWidth(value);
    }

    private void applyImageHeight(double value) {
        appSettings.setImageHeight(value);
    }

    private void parseAndApply(String text, java.util.function.DoubleConsumer apply, Slider slider) {
        try {
            double value = Double.parseDouble(text.trim());
            apply.accept(value);
            suppressSizeSync = true;
            slider.setValue(value);
            suppressSizeSync = false;
        } catch (NumberFormatException ignored) {
            // некоректне число в полі — просто ігноруємо, повзунок лишає попереднє значення
        }
    }

    @FXML
    private void handleResetSizes() {
        appSettings.resetToDefaults();
        suppressSizeSync = true;
        cardWidthSlider.setValue(appSettings.cardWidthProperty().get());
        imageHeightSlider.setValue(appSettings.imageHeightProperty().get());
        suppressSizeSync = false;
        cardWidthField.setText(formatSize(appSettings.cardWidthProperty().get()));
        imageHeightField.setText(formatSize(appSettings.imageHeightProperty().get()));
    }

    private static String formatSize(double value) {
        return String.valueOf(Math.round(value));
    }

    @FXML
    private void handleChangePassword() {
        String current = currentPasswordField.getText();
        String newPass = newPasswordField.getText();
        String confirm = confirmPasswordField.getText();

        if (current.isEmpty() || newPass.isEmpty()) {
            showPasswordStatus("Заповніть усі поля", true);
            return;
        }
        if (newPass.length() < 8) {
            showPasswordStatus("Новий пароль — мінімум 8 символів", true);
            return;
        }
        if (!newPass.equals(confirm)) {
            showPasswordStatus("Паролі не збігаються", true);
            return;
        }

        changePasswordButton.setDisable(true);
        showPasswordStatus("Зміна пароля...", false);

        Thread.ofVirtual().start(() -> {
            try {
                UserApiService.ActionResult result = userApiService.changePassword(current, newPass);
                Platform.runLater(() -> {
                    changePasswordButton.setDisable(false);
                    if (result.success()) {
                        showPasswordStatus("Пароль змінено", false);
                        currentPasswordField.clear();
                        newPasswordField.clear();
                        confirmPasswordField.clear();
                    } else {
                        showPasswordStatus(result.errorMessage(), true);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    changePasswordButton.setDisable(false);
                    showPasswordStatus("Помилка з'єднання: " + e.getMessage(), true);
                });
            }
        });
    }

    @FXML
    private void handleDeleteAccount() {
        String password = deleteConfirmPasswordField.getText();
        if (password.isEmpty()) {
            showDeleteStatus("Введіть пароль для підтвердження", true);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Акаунт буде видалено назавжди разом з усіма постами, коментарями й повідомленнями. Продовжити?");
        confirm.setHeaderText(null);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        deleteAccountButton.setDisable(true);
        showDeleteStatus("Видалення акаунту...", false);

        Thread.ofVirtual().start(() -> {
            try {
                UserApiService.ActionResult deleteResult = userApiService.deleteAccount(password);
                Platform.runLater(() -> {
                    deleteAccountButton.setDisable(false);
                    if (deleteResult.success()) {
                        performLogout();
                    } else {
                        showDeleteStatus(deleteResult.errorMessage(), true);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    deleteAccountButton.setDisable(false);
                    showDeleteStatus("Помилка з'єднання: " + e.getMessage(), true);
                });
            }
        });
    }

    private void performLogout() {
        ChatConnection.getInstance().disconnect();
        UnreadCounter.getInstance().reset();
        SessionManager.getInstance().clear();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) deleteAccountButton.getScene().getWindow();
            stage.setScene(SceneUtil.create(root, 400, 400));
            stage.setTitle("Соціальна мережа");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showPasswordStatus(String message, boolean error) {
        passwordStatusLabel.getStyleClass().setAll(error ? "error-label" : "secondary-label");
        passwordStatusLabel.setText(message);
    }

    private void showDeleteStatus(String message, boolean error) {
        deleteStatusLabel.getStyleClass().setAll(error ? "error-label" : "secondary-label");
        deleteStatusLabel.setText(message);
    }

    private void buildPresetButtons() {
        presetButtonsBox.getChildren().clear();
        for (AppSettings.SizePreset preset : AppSettings.SizePreset.values()) {
            Button b = new Button(String.valueOf(preset.getLevel()));
            b.getStyleClass().addAll("button", "button-secondary");
            b.setTooltip(new javafx.scene.control.Tooltip(preset.getLabel()));
            b.setOnAction(e -> applyPreset(preset));
            presetButtonsBox.getChildren().add(b);
        }
        highlightActivePreset();
    }

    private void applyPreset(AppSettings.SizePreset preset) {
        appSettings.applyPreset(preset);

        suppressSizeSync = true;
        cardWidthSlider.setValue(preset.getCardWidth());
        imageHeightSlider.setValue(preset.getImageHeight());
        suppressSizeSync = false;

        cardWidthField.setText(formatSize(preset.getCardWidth()));
        imageHeightField.setText(formatSize(preset.getImageHeight()));

        highlightActivePreset();
    }

    private void highlightActivePreset() {
        AppSettings.SizePreset active = appSettings.closestPreset();
        for (var node : presetButtonsBox.getChildren()) {
            if (node instanceof Button b) {
                boolean isActive = b.getText().equals(String.valueOf(active.getLevel()));
                b.getStyleClass().setAll("button", isActive ? "button-primary" : "button-secondary");
            }
        }
    }

    private void bindThemeControls() {
        lightThemeRadio.setUserData(Theme.LIGHT);
        darkThemeRadio.setUserData(Theme.DARK);
        customThemeRadio.setUserData(Theme.CUSTOM);

        suppressThemeSync = true;
        switch (themeManager.getTheme()) {
            case LIGHT -> themeToggleGroup.selectToggle(lightThemeRadio);
            case DARK -> themeToggleGroup.selectToggle(darkThemeRadio);
            case CUSTOM -> themeToggleGroup.selectToggle(customThemeRadio);
        }
        suppressThemeSync = false;

        customBgPicker.setValue(Color.web(themeManager.getCustomBg()));
        customAccentPicker.setValue(Color.web(themeManager.getCustomAccent()));
        customTextPicker.setValue(Color.web(themeManager.getCustomText()));

        updateCustomColorsVisibility();

        themeToggleGroup.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (suppressThemeSync || selected == null) {
                return;
            }
            Theme chosen = (Theme) selected.getUserData();
            themeManager.setTheme(chosen);
            applyThemeLive();
            updateCustomColorsVisibility();
        });

        customBgPicker.valueProperty().addListener((obs, old, value) -> applyCustomColorsLive());
        customAccentPicker.valueProperty().addListener((obs, old, value) -> applyCustomColorsLive());
        customTextPicker.valueProperty().addListener((obs, old, value) -> applyCustomColorsLive());
    }

    private void updateCustomColorsVisibility() {
        boolean show = themeManager.getTheme() == Theme.CUSTOM;
        customColorsBox.setVisible(show);
        customColorsBox.setManaged(show);
    }

    private void applyCustomColorsLive() {
        if (themeManager.getTheme() != Theme.CUSTOM) {
            return;
        }
        themeManager.setCustomColors(
                toHex(customBgPicker.getValue()),
                toHex(customAccentPicker.getValue()),
                toHex(customTextPicker.getValue()));
        applyThemeLive();
    }

    private void applyThemeLive() {
        if (deleteAccountButton.getScene() != null) {
            themeManager.applyTo(deleteAccountButton.getScene());
        }
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }
}