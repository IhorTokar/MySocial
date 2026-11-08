package com.example.social.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/** Екран адмін-панелі: вкладки з окремими контролерами (fx:include). */
public class AdminPanelController {

    @FXML private TabPane tabPane;
    @FXML private Tab usersTab;
    @FXML private Tab graphTab;
    @FXML private Tab auditTab;

    /** Контролери вкладених FXML: ім'я поля = fx:id включення + "Controller". */
    @FXML private AdminWeightsController weightsController;
    @FXML private AdminUsersController usersController;
    @FXML private AdminContentController contentController;
    @FXML private AdminGraphController graphController;
    @FXML private AdminAuditController auditController;

    private MainShellController shell;

    @FXML
    private void initialize() {
        // Інші вкладки додають записи в журнал, тому він оновлюється при кожному переході на нього
        auditTab.selectedProperty().addListener((obs, wasSelected, selected) -> {
            if (selected) {
                auditController.refresh();
            }
        });

        // «Керувати автором» на вкладці «Контент» відкриває користувача на сусідній вкладці
        contentController.setOnOpenUser(this::openUser);
        graphController.setOnOpenUser(this::openUser);

        // Граф завантажується й розкладається лише коли вкладку вперше відкрили
        graphTab.selectedProperty().addListener((obs, wasSelected, selected) -> {
            if (selected) {
                graphController.onShown();
            }
        });
    }

    public void setShell(MainShellController shell) {
        this.shell = shell;
        usersController.setShell(shell);
    }

    /** Перейти на вкладку «Користувачі» й відкрити картку користувача з цим ID. */
    public void openUser(long userId) {
        tabPane.getSelectionModel().select(usersTab);
        usersController.showUser(userId);
    }

    MainShellController getShell() {
        return shell;
    }
}