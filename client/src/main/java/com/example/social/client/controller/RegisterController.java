package com.example.social.client.controller;

import com.example.social.client.service.ApiClient;
import com.example.social.client.service.AuthApiService;
import javafx.application.Platform;
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
import java.util.regex.Pattern;

public class RegisterController {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

    @FXML
    private TextField usernameField;

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Label errorLabel;

    @FXML
    private Label successLabel;

    @FXML
    private Button registerButton;

    private final AuthApiService authApiService = new AuthApiService(new ApiClient());

    @FXML
    private void handleRegister() {
        errorLabel.setText("");
        successLabel.setText("");

        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        String validationError = validate(username, email, password, confirmPassword);
        if (validationError != null) {
            errorLabel.setText(validationError);
            return;
        }

        registerButton.setDisable(true);

        Thread.ofVirtual().start(() -> {
            try {
                AuthApiService.RegisterResult result = authApiService.register(username, email, password);

                Platform.runLater(() -> {
                    registerButton.setDisable(false);

                    if (result.success()) {
                        successLabel.setText("Акаунт створено! Тепер можна увійти.");
                        clearFields();
                    } else {
                        errorLabel.setText(result.errorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    registerButton.setDisable(false);
                    errorLabel.setText("Не вдалося з'єднатись із сервером");
                });
            }
        });
    }

    private String validate(String username, String email, String password, String confirmPassword) {
        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            return "Заповніть усі поля";
        }
        if (username.length() < 3) {
            return "Ім'я користувача — мінімум 3 символи";
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return "Некоректний email";
        }
        if (password.length() < 8) {
            return "Пароль — мінімум 8 символів";
        }
        if (!password.equals(confirmPassword)) {
            return "Паролі не збігаються";
        }
        return null;
    }

    private void clearFields() {
        usernameField.clear();
        emailField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
    }

    @FXML
    private void handleBackToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) registerButton.getScene().getWindow();
            stage.setScene(new Scene(root, 400, 400));
            stage.setTitle("Соціальна мережа");
        } catch (IOException e) {
            errorLabel.setText("Помилка переходу: " + e.getMessage());
        }
    }
}