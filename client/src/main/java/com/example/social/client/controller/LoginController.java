package com.example.social.client.controller;

import com.example.social.client.service.ApiClient;
import com.example.social.client.service.AuthApiService;
import com.example.social.client.util.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label errorLabel;

    @FXML
    private Button loginButton;

    private final AuthApiService authApiService = new AuthApiService(new ApiClient());

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Заповніть обидва поля");
            return;
        }

        loginButton.setDisable(true);
        errorLabel.setText("");

        Thread.ofVirtual().start(() -> {
            try {
                AuthApiService.LoginResult result = authApiService.login(username, password);

                javafx.application.Platform.runLater(() -> {
                    loginButton.setDisable(false);

                    if (result.success()) {
                        SessionManager.getInstance().setSession(
                                result.token(), result.userId(), result.username());
                        goToFeed();
                    } else {
                        errorLabel.setText(result.errorMessage());
                    }
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    loginButton.setDisable(false);
                    errorLabel.setText("Не вдалося з'єднатись із сервером: " + e.getMessage());
                });
            }
        });
    }

    @FXML
    private void handleGoToRegister() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/register.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(new Scene(root, 400, 500));
            stage.setTitle("Реєстрація");
        } catch (IOException e) {
            errorLabel.setText("Помилка переходу: " + e.getMessage());
        }
    }

    private void goToFeed() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/feed.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(new Scene(root, 900, 600));
            stage.setTitle("Стрічка");
        } catch (IOException e) {
            errorLabel.setText("Помилка завантаження стрічки: " + e.getMessage());
        }
    }
}