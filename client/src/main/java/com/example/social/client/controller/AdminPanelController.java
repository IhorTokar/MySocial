package com.example.social.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Tab;

/** Екран адмін-панелі: вкладки з окремими контролерами (fx:include). */
public class AdminPanelController {

    @FXML private Tab auditTab;

    /** Контролери вкладених FXML: ім'я поля = fx:id включення + "Controller". */
    @FXML private AdminWeightsController weightsController;
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
    }

    public void setShell(MainShellController shell) {
        this.shell = shell;
    }

    MainShellController getShell() {
        return shell;
    }
}