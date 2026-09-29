package com.example.social.client.controller;

import com.example.social.client.service.ApiClient;
import com.example.social.client.service.UserApiService;
import com.example.social.client.util.AvatarUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public class EditProfileController {

    private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;

    @FXML private StackPane avatarPreview;
    @FXML private Button changeAvatarButton;
    @FXML private TextField displayNameField;
    @FXML private TextArea aboutMeArea;
    @FXML private Label statusLabel;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private final UserApiService userApiService = new UserApiService(new ApiClient());
    private ProfileController parentProfileController;
    private UserApiService.UserProfile profile;

    public void setParentProfileController(ProfileController parent) {
        this.parentProfileController = parent;
    }

    public void prefill(UserApiService.UserProfile profile) {
        this.profile = profile;
        displayNameField.setText(profile.displayName() != null ? profile.displayName() : "");
        aboutMeArea.setText(profile.aboutMe() != null ? profile.aboutMe() : "");
        refreshAvatarPreview();
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
                "Зображення", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));

        File file = chooser.showOpenDialog(changeAvatarButton.getScene().getWindow());
        if (file == null) {
            return;
        }
        if (file.length() > MAX_AVATAR_BYTES) {
            showError("Файл завеликий (максимум 5 МБ)");
            return;
        }

        changeAvatarButton.setDisable(true);
        statusLabel.getStyleClass().setAll("secondary-label");
        statusLabel.setText("Завантаження фото...");

        Thread.ofVirtual().start(() -> {
            try {
                UserApiService.UserProfileResult result = userApiService.uploadAvatar(file.toPath());

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
            }
        });
    }

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

    private void closeWindow() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }
}