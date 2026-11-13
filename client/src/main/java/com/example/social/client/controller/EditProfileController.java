package com.example.social.client.controller;

import com.example.social.client.component.CoverPane;
import com.example.social.client.service.ApiClient;
import com.example.social.client.service.UserApiService;
import com.example.social.client.util.AvatarUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import com.example.social.client.component.AvatarCropDialog;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class EditProfileController {

    /** Обмеження для вихідного фото; на сервер іде вже обрізаний PNG до 512×512. */
    private static final long MAX_AVATAR_BYTES = 20L * 1024 * 1024;
    private static final long MAX_COVER_BYTES = 20L * 1024 * 1024;

    @FXML private StackPane avatarPreview;
    @FXML private Button changeAvatarButton;
    @FXML private TextField displayNameField;
    @FXML private TextArea aboutMeArea;
    @FXML private Label statusLabel;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    @FXML private CoverPane coverPreview;
    @FXML private Button chooseCoverButton;
    @FXML private Button removeCoverButton;
    @FXML private VBox coverPendingBox;
    @FXML private HBox coverFocusBox;
    @FXML private Label coverFocusLabel;
    @FXML private Slider coverFocusSlider;
    @FXML private Button saveCoverButton;

    private final UserApiService userApiService = new UserApiService(new ApiClient());

    private ProfileController parentProfileController;
    private UserApiService.UserProfile profile;
    /** Обране, але ще не збережене зображення обкладинки. */
    private Image pendingCover;

    @FXML
    private void initialize() {
        coverFocusSlider.valueProperty().addListener((obs, old, value) -> coverPreview.setFocus(value.doubleValue()));
    }

    public void setParentProfileController(ProfileController parent) {
        this.parentProfileController = parent;
    }

    public void prefill(UserApiService.UserProfile profile) {
        this.profile = profile;
        displayNameField.setText(profile.displayName() != null ? profile.displayName() : "");
        aboutMeArea.setText(profile.aboutMe() != null ? profile.aboutMe() : "");
        refreshAvatarPreview();
        showSavedCover();
    }

    private void refreshAvatarPreview() {
        avatarPreview.getChildren().setAll(
                AvatarUtil.create(profile.displayNameOrUsername(), profile.userAvatarUrl(), 72));
    }

    @FXML
    private void handleChangeAvatar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Оберіть фото профілю");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Зображення", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));

        File file = chooser.showOpenDialog(changeAvatarButton.getScene().getWindow());
        if (file == null) {
            return;
        }
        if (file.length() > MAX_AVATAR_BYTES) {
            showError("Файл завеликий (максимум 20 МБ)");
            return;
        }

        changeAvatarButton.setDisable(true);
        showInfo("Відкриття зображення...");
        Thread.ofVirtual().start(() -> {
            Image image = new Image(file.toURI().toString());
            Platform.runLater(() -> {
                changeAvatarButton.setDisable(false);
                if (image.isError() || image.getWidth() <= 0) {
                    showError("Не вдалося відкрити зображення (підтримуються PNG, JPEG, GIF, BMP)");
                    return;
                }
                statusLabel.setText("");
                // кадрування: користувач обирає, яка частина фото потрапить у круглий аватар
                Path cropped = AvatarCropDialog.show(changeAvatarButton.getScene().getWindow(), image);
                if (cropped != null) {
                    uploadAvatar(cropped);
                }
            });
        });
    }

    private void uploadAvatar(Path cropped) {
        changeAvatarButton.setDisable(true);
        showInfo("Завантаження фото...");

        Thread.ofVirtual().start(() -> {
            try {
                UserApiService.UserProfileResult result = userApiService.uploadAvatar(cropped);

                Platform.runLater(() -> {
                    changeAvatarButton.setDisable(false);
                    if (result.success()) {
                        profile = result.profile();
                        refreshAvatarPreview();
                        if (parentProfileController != null) {
                            parentProfileController.applyProfile(result.profile());
                        }
                        statusLabel.getStyleClass().setAll("success-label");
                        statusLabel.setText("Фото оновлено");
                    } else {
                        showError(result.errorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    changeAvatarButton.setDisable(false);
                    showError("Помилка з'єднання: " + e.getMessage());
                });
            } finally {
                deleteQuietly(cropped);
            }
        });
    }

    // ===== Обкладинка =====

    private void showSavedCover() {
        pendingCover = null;
        coverPreview.load(profile.coverUrl());
        setVisible(coverPendingBox, false);
        chooseCoverButton.setDisable(false);
        removeCoverButton.setDisable(profile.coverUrl() == null);
    }

    @FXML
    private void handleChooseCover() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Оберіть обкладинку (3:1, напр. 1500×500)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Зображення", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));

        File file = chooser.showOpenDialog(chooseCoverButton.getScene().getWindow());
        if (file == null) {
            return;
        }
        if (file.length() > MAX_COVER_BYTES) {
            showError("Файл завеликий (максимум 20 МБ)");
            return;
        }

        chooseCoverButton.setDisable(true);
        showInfo("Відкриття зображення...");
        Thread.ofVirtual().start(() -> {
            // повна роздільність потрібна для якісної обрізки
            Image image = new Image(file.toURI().toString());
            Platform.runLater(() -> {
                chooseCoverButton.setDisable(false);
                if (image.isError() || image.getWidth() <= 0) {
                    showError("Не вдалося відкрити зображення (підтримуються PNG, JPEG, GIF, BMP)");
                    return;
                }
                if (!CoverPane.isLargeEnough(image)) {
                    showError("Зображення замале: " + (int) image.getWidth() + "×" + (int) image.getHeight()
                            + ". Потрібно щонайменше " + CoverPane.MIN_WIDTH + "×" + CoverPane.MIN_HEIGHT
                            + " (рекомендовано 1500×500).");
                    return;
                }
                showPendingCover(image);
            });
        });
    }

    /** Попередній перегляд: якщо пропорції не 3:1, повзунком обирається, яку частину залишити. */
    private void showPendingCover(Image image) {
        pendingCover = image;
        coverPreview.setImage(image);
        coverFocusSlider.setValue(0.5);
        coverPreview.setFocus(0.5);

        boolean positioning = CoverPane.needsPositioning(image);
        setVisible(coverFocusBox, positioning);
        coverFocusLabel.setText(image.getWidth() / image.getHeight() > CoverPane.RATIO
                ? "Зсув по горизонталі" : "Зсув по вертикалі");
        setVisible(coverPendingBox, true);
        removeCoverButton.setDisable(true);
        showInfo((int) image.getWidth() + "×" + (int) image.getHeight()
                + (positioning ? " — оберіть частину, яку залишити" : " — пропорції 3:1, обрізка не потрібна"));
    }

    @FXML
    private void handleSaveCover() {
        if (pendingCover == null) {
            return;
        }
        Image image = pendingCover;
        double focus = coverPreview.getFocus();
        saveCoverButton.setDisable(true);
        showInfo("Завантаження обкладинки...");

        Thread.ofVirtual().start(() -> {
            Path cropped = null;
            try {
                cropped = CoverPane.exportCrop(image, focus);
                UserApiService.UserProfileResult result = userApiService.uploadCover(cropped);
                Platform.runLater(() -> {
                    saveCoverButton.setDisable(false);
                    if (result.success()) {
                        applyUpdatedProfile(result.profile());
                        showSuccess("Обкладинку оновлено");
                    } else {
                        showError(result.errorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    saveCoverButton.setDisable(false);
                    showError("Помилка: " + e.getMessage());
                });
            } finally {
                deleteQuietly(cropped);
            }
        });
    }

    @FXML
    private void handleCancelCover() {
        showSavedCover();
        statusLabel.setText("");
    }

    @FXML
    private void handleRemoveCover() {
        removeCoverButton.setDisable(true);
        Thread.ofVirtual().start(() -> {
            try {
                UserApiService.UserProfileResult result = userApiService.removeCover();
                Platform.runLater(() -> {
                    if (result.success()) {
                        applyUpdatedProfile(result.profile());
                        showSuccess("Обкладинку прибрано");
                    } else {
                        removeCoverButton.setDisable(false);
                        showError(result.errorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    removeCoverButton.setDisable(false);
                    showError("Помилка з'єднання: " + e.getMessage());
                });
            }
        });
    }

    /** Нові дані профілю — і в цьому вікні, і на сторінці профілю під ним. */
    private void applyUpdatedProfile(UserApiService.UserProfile updated) {
        profile = updated;
        showSavedCover();
        if (parentProfileController != null) {
            parentProfileController.applyProfile(updated);
        }
    }

    private static void setVisible(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static void deleteQuietly(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (Exception ignored) {
                // тимчасовий файл — не критично
            }
        }
    }

    // ===== Ім'я та опис =====

    @FXML
    private void handleSave() {
        saveButton.setDisable(true);
        statusLabel.getStyleClass().setAll("secondary-label");
        statusLabel.setText("Збереження...");

        String displayName = displayNameField.getText().trim();
        String aboutMe = aboutMeArea.getText().trim();

        Thread.ofVirtual().start(() -> {
            try {
                UserApiService.UserProfileResult result = userApiService.updateProfile(displayName, aboutMe);

                Platform.runLater(() -> {
                    saveButton.setDisable(false);
                    if (result.success()) {
                        if (parentProfileController != null) {
                            parentProfileController.applyProfile(result.profile());
                        }
                        closeWindow();
                    } else {
                        showError(result.errorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    saveButton.setDisable(false);
                    showError("Помилка з'єднання: " + e.getMessage());
                });
            }
        });
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    private void showError(String message) {
        statusLabel.getStyleClass().setAll("error-label");
        statusLabel.setText(message);
    }

    private void showInfo(String message) {
        statusLabel.getStyleClass().setAll("secondary-label");
        statusLabel.setText(message);
    }

    private void showSuccess(String message) {
        statusLabel.getStyleClass().setAll("success-label");
        statusLabel.setText(message);
    }

    private void closeWindow() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }
}