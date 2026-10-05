package com.example.social.client.util;

import javafx.scene.Parent;
import javafx.scene.Scene;

public class SceneUtil {

    private SceneUtil() {
    }

    public static Scene create(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        ThemeManager.getInstance().applyTo(scene);
        return scene;
    }
}