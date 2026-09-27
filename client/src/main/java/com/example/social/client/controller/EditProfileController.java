package com.example.social.client.controller;

import com.example.social.client.service.ApiClient;
import com.example.social.client.service.UserApiService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class EditProfileController {

    @FXML private TextField displayNameField;
    @FXML private TextArea aboutMeArea;
    @FXML private Label statusLabel;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private final UserApiService userApiService = new UserApiService(new ApiClient());
    private ProfileController parentProfileController;

    public void setParentProfileController(ProfileController parent) {
        this.parentProfileController = parent;
    }

    public void prefill(String displayName, String aboutMe) {
        displayNameField.setText(displayName != null ? displayName : "");
        aboutMeArea.setText(aboutMe != null ? aboutMe : "");
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
                        statusLabel.getStyleClass().setAll("error-label");
                        statusLabel.setText(result.errorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    saveButton.setDisable(false);
                    statusLabel.getStyleClass().setAll("error-label");
                    statusLabel.setText("Помилка з'єднання: " + e.getMessage());
                });
            }
        });
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }
}