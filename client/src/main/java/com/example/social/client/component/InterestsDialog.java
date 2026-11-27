package com.example.social.client.component;

import com.example.social.client.service.ApiClient;
import com.example.social.client.service.InsightsApiService;
import com.example.social.client.util.SceneUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

/**
 * «Що вам цікаво?» — вибір тем. З'являється при першому вході людини без вподобань і підписок
 * (холодний старт: стрічці «Для вас» ще немає на що спертися) і відкривається зі сторінки «Моя стрічка».
 */
public final class InterestsDialog {

    private InterestsDialog() {
    }

    /**
     * @param interests поточні теми й варіанти (вже завантажені)
     * @param onSaved   після «Зберегти» або «Пропустити» — напр., оновити стрічку
     */
    public static void show(Window owner, InsightsApiService.Interests interests, Runnable onSaved) {
        Stage stage = new Stage();

        Label title = new Label("Що вам цікаво?");
        title.getStyleClass().add("title-label");
        Label hint = new Label("Оберіть кілька тем — стрічка «Для вас» одразу почне підбирати пости про них. "
                + "Коли ви почнете вподобувати пости й підписуватися на людей, стрічка враховуватиме і це. "
                + "Змінити теми можна будь-коли на сторінці «Моя стрічка».");
        hint.getStyleClass().add("secondary-label");
        hint.setWrapText(true);

        FlowPane chips = new FlowPane(8, 8);
        List<ToggleButton> toggles = new ArrayList<>();
        Label counter = new Label();
        counter.getStyleClass().add("secondary-label");
        Runnable updateCounter = () -> {
            long chosen = toggles.stream().filter(ToggleButton::isSelected).count();
            counter.setText("Обрано: " + chosen + " з " + interests.maxInterests());
            toggles.forEach(t -> t.setDisable(!t.isSelected() && chosen >= interests.maxInterests()));
        };
        for (InsightsApiService.TagOption option : interests.options()) {
            ToggleButton chip = new ToggleButton("#" + option.tag() + "  " + option.posts());
            chip.setUserData(option.tag());
            chip.getStyleClass().add("interest-chip");
            chip.setSelected(interests.selected().contains(option.tag()));
            chip.selectedProperty().addListener((obs, old, selected) -> updateCounter.run());
            toggles.add(chip);
            chips.getChildren().add(chip);
        }
        updateCounter.run();

        ScrollPane scroll = new ScrollPane(chips);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Label status = new Label();
        status.getStyleClass().add("error-label");
        status.setWrapText(true);

        Button save = new Button("Зберегти");
        save.getStyleClass().addAll("button", "button-primary");
        Button skip = new Button("Пропустити");
        skip.getStyleClass().addAll("button", "button-secondary");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(10, counter, spacer, skip, save);
        buttons.setAlignment(Pos.CENTER_LEFT);

        save.setOnAction(e -> submit(stage, status, save, skip, toggles.stream()
                .filter(ToggleButton::isSelected).map(t -> (String) t.getUserData()).toList(), onSaved));
        // «Пропустити» на першому вході зберігає порожній вибір, щоб вікно більше не з'являлось само
        skip.setOnAction(e -> {
            if (interests.needsOnboarding()) {
                submit(stage, status, save, skip, List.of(), onSaved);
            } else {
                stage.close();
            }
        });

        VBox root = new VBox(12, title, hint, scroll, status, buttons);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("dialog-root");

        stage.setTitle("Ваші теми");
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setScene(SceneUtil.create(root, 560, 460));
        stage.show();
    }

    private static void submit(Stage stage, Label status, Button save, Button skip, List<String> tags, Runnable onSaved) {
        save.setDisable(true);
        skip.setDisable(true);
        Thread.ofVirtual().start(() -> {
            try {
                new InsightsApiService(new ApiClient()).saveInterests(tags);
                Platform.runLater(() -> {
                    stage.close();
                    if (onSaved != null) {
                        onSaved.run();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    save.setDisable(false);
                    skip.setDisable(false);
                    status.setText(ex.getMessage());
                });
            }
        });
    }
}
