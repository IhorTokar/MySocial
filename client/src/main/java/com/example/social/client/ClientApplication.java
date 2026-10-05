package com.example.social.client;

import com.example.social.client.util.SceneUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;

public class ClientApplication extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
        Parent root = loader.load();

        stage.setTitle("Соціальна мережа");
        stage.setScene(SceneUtil.create(root, 400, 400));
        stage.show();
    }

    @Override
    public void stop() {
        com.example.social.client.service.ChatConnection.getInstance().disconnect();
    }

    public static void main(String[] args) {
        launch(args);
    }
}