package com.example.social.client.component;

import com.example.social.client.service.PostApiService;
import com.example.social.client.util.SceneUtil;
import javafx.collections.ListChangeListener;
import javafx.scene.control.ListView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.function.Consumer;

/**
 * Один пост в окремому вікні (клік по картці або сповіщення): та сама картка, що й у стрічці,
 * з одразу розгорнутими коментарями. Esc закриває вікно.
 */
public final class PostViewDialog {

    private PostViewDialog() {
    }

    public static void show(Window owner, PostApiService.PostItem post, Consumer<Long> onOpenProfile) {
        show(owner, post, onOpenProfile, updated -> { });
    }

    /**
     * @param onOpenProfile клік по автору — вікно закривається й відкривається профіль
     * @param onChanged     лайк, збереження чи новий коментар у вікні — щоб оновити картку в стрічці
     */
    public static void show(Window owner, PostApiService.PostItem post,
                            Consumer<Long> onOpenProfile, Consumer<PostApiService.PostItem> onChanged) {
        Stage stage = new Stage();
        ListView<PostApiService.PostItem> list = new ListView<>();
        list.getStyleClass().add("list-view");
        list.setCellFactory(l -> new PostListCell(userId -> {
            stage.close();
            onOpenProfile.accept(userId);
        }).inDialog());
        list.getItems().setAll(post);
        list.getItems().addListener((ListChangeListener<PostApiService.PostItem>) change -> {
            while (change.next()) {
                if (change.wasReplaced() || change.wasAdded()) {
                    change.getAddedSubList().forEach(onChanged);
                }
            }
        });

        stage.setTitle("Пост @" + post.authorUsername());
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setScene(SceneUtil.create(list, 760, 680));
        stage.getScene().setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                stage.close();
            }
        });
        stage.show();
    }
}