package com.example.social.client.controller;

import com.example.social.client.service.ApiClient;
import com.example.social.client.service.AuthApiService;
import com.example.social.client.util.SceneUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

/** Екран «Забули пароль?»: заявка адміністратору (без входу в акаунт). */
public class ForgotPasswordController {

    @FXML private TextField loginField;
    @FXML private TextField contactField;
    @FXML private TextArea messageField;
    @FXML private Label errorLabel;
    @FXML private Label successLabel;
    @FXML private Button submitButton;

    private final AuthApiService authApiService = new AuthApiService(new ApiClient());

    /** Логін, уже введений на екрані входу. */
    public void setLogin(String login) {
        if (login != null) {
            loginField.setText(login.trim());
        }
    }

    @FXML
    private void handleSubmit() {
        String login = loginField.getText() == null ? "" : loginField.getText().trim();
        String contact = contactField.getText() == null ? "" : contactField.getText().trim();
        errorLabel.setText("");
        successLabel.setText("");

        if (login.isEmpty()) {
            errorLabel.setText("Вкажіть ім'я користувача або email акаунта");
            return;
        }
        if (contact.isEmpty()) {
            errorLabel.setText("Вкажіть, як з вами зв'язатися — інакше адміністратор не зможе передати пароль");
            return;
        }

        submitButton.setDisable(true);
        Thread.ofVirtual().start(() -> {
            try {
                String error = authApiService.requestPasswordReset(login, contact, messageField.getText());
                Platform.runLater(() -> {
                    if (error == null) {
                        successLabel.setText("Заявку надіслано. Адміністратор зв'яжеться з вами за вказаним контактом.");
                        contactField.clear();
                        messageField.clear();
                    } else {
                        errorLabel.setText(error);
                        submitButton.setDisable(false);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    errorLabel.setText("Помилка з'єднання: " + e.getMessage());
                    submitButton.setDisable(false);
                });
            }
        });
    }

    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) submitButton.getScene().getWindow();
            SceneUtil.switchScene(stage, root, 400, 400);
            stage.setTitle("Соціальна мережа");
        } catch (IOException e) {
            errorLabel.setText("Помилка переходу: " + e.getMessage());
        }
    }
}