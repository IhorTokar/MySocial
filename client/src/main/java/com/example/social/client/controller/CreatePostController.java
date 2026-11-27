package com.example.social.client.controller;

import com.example.social.client.service.ApiClient;
import com.example.social.client.service.PostApiService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import java.util.function.Consumer;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CreatePostController {

    private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;

    @FXML private Label titleLabel;
    @FXML private TextField labelField;
    @FXML private TextArea textArea;
    @FXML private TextField tagsField;
    @FXML private FlowPane tagSuggestionsPane;
    @FXML private ImageView imagePreview;
    @FXML private Button chooseImageButton;
    @FXML private Button removeImageButton;
    @FXML private Label statusLabel;
    @FXML private Button submitButton;
    @FXML private Button cancelButton;

    private final PostApiService postApiService = new PostApiService(new ApiClient());
    private Runnable onPublished;
    private Runnable onCancelled;
    /** Отримує id щойно створеного поста (не викликається при редагуванні). */
    private Consumer<Long> onPostPublished;

    private Long editingPostId;
    private Path selectedImagePath;

    /** Підказки тегів просимо, коли автор на мить зупинився, а не на кожну літеру. */
    private final PauseTransition suggestDelay = new PauseTransition(Duration.millis(900));
    /** Номер запиту підказок: відповідь на застарілий текст ігнорується. */
    private int suggestRequest;
    private static final int MIN_TEXT_FOR_SUGGESTIONS = 15;

    @FXML
    private void initialize() {
        suggestDelay.setOnFinished(e -> requestTagSuggestions());
        textArea.textProperty().addListener((obs, old, text) -> suggestDelay.playFromStart());
        tagsField.textProperty().addListener((obs, old, text) -> suggestDelay.playFromStart());
    }

    /** Теги найближчих за змістом постів; уже вписані в поле — не пропонуються. */
    private void requestTagSuggestions() {
        String text = textArea.getText() == null ? "" : textArea.getText().trim();
        int request = ++suggestRequest;
        if (text.length() < MIN_TEXT_FOR_SUGGESTIONS) {
            showSuggestions(List.of());
            return;
        }
        List<String> current = lenientTags(tagsField.getText());
        Thread.ofVirtual().start(() -> {
            List<PostApiService.TagSuggestion> suggestions;
            try {
                suggestions = postApiService.suggestTags(text, current);
            } catch (Exception e) {
                suggestions = List.of();   // підказки необов'язкові — без них форма працює як завжди
            }
            List<PostApiService.TagSuggestion> result = suggestions;
            Platform.runLater(() -> {
                if (request == suggestRequest) {
                    showSuggestions(result);
                }
            });
        });
    }

    private void showSuggestions(List<PostApiService.TagSuggestion> suggestions) {
        tagSuggestionsPane.getChildren().clear();
        if (!suggestions.isEmpty()) {
            Label caption = new Label("Схожі пости мають теги:");
            caption.getStyleClass().add("secondary-label");
            tagSuggestionsPane.getChildren().add(caption);
            for (PostApiService.TagSuggestion s : suggestions) {
                Button chip = new Button("+ #" + s.tag());
                chip.getStyleClass().add("tag-suggestion");
                chip.setTooltip(new javafx.scene.control.Tooltip(String.format(java.util.Locale.ROOT,
                        "%.0f%% «голосів» найближчих за змістом постів", s.share() * 100)));
                chip.setOnAction(e -> addTag(s.tag()));
                tagSuggestionsPane.getChildren().add(chip);
            }
        }
        show(tagSuggestionsPane, !suggestions.isEmpty());
    }

    private void addTag(String tag) {
        String current = tagsField.getText() == null ? "" : tagsField.getText().trim();
        tagsField.setText(current.isEmpty() ? "#" + tag : current + " #" + tag);
        tagsField.positionCaret(tagsField.getText().length());
    }

    /** Теги з поля без перевірки формату — лише щоб не пропонувати те, що вже вписано. */
    private static List<String> lenientTags(String raw) {
        List<String> tags = new ArrayList<>();
        if (raw != null) {
            for (String token : raw.trim().split("\\s+")) {
                String tag = token.replaceFirst("^#+", "").trim();
                if (!tag.isEmpty()) {
                    tags.add(tag);
                }
            }
        }
        return tags;
    }

    public void setOnPublished(Runnable onPublished) {
        this.onPublished = onPublished;
    }

    public void setOnCancelled(Runnable onCancelled) {
        this.onCancelled = onCancelled;
    }

    public void setOnPostPublished(Consumer<Long> onPostPublished) {
        this.onPostPublished = onPostPublished;
    }

    /** Курсор — одразу в поле тексту (поле «Що у вас нового?» у стрічці). */
    public void focusText() {
        textArea.requestFocus();
    }

    /** Відкрити вибір фото без натискання «Додати фото» (кнопка-іконка в згорнутому полі). */
    public void chooseImage() {
        handleChooseImage();
    }

    public void setEditMode(PostApiService.PostItem post) {
        this.editingPostId = post.postId();
        labelField.setText(post.label() != null ? post.label() : "");
        textArea.setText(post.text());
        tagsField.setText(post.tags().stream()
                .map(t -> "#" + t)
                .reduce((a, b) -> a + " " + b)
                .orElse(""));

        if (titleLabel != null) {
            titleLabel.setText("Редагувати пост");
        }
        submitButton.setText("Зберегти зміни");

        if (post.mediaUrl() != null && !post.mediaUrl().isBlank()) {
            String fullUrl = post.mediaUrl().startsWith("http")
                    ? post.mediaUrl() : ApiClient.BASE_URL + post.mediaUrl();
            imagePreview.setImage(new Image(fullUrl, 400, 0, true, true, true));
            show(imagePreview, true);
            show(removeImageButton, true);
        }
    }

    @FXML
    private void handleChooseImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Оберіть зображення");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Зображення", "*.png", "*.jpg", "*.jpeg", "*.gif"));

        File file = chooser.showOpenDialog(chooseImageButton.getScene().getWindow());
        if (file == null) {
            return;
        }
        if (file.length() > MAX_IMAGE_BYTES) {
            statusLabel.getStyleClass().setAll("error-label");
            statusLabel.setText("Файл завеликий (максимум 20 МБ)");
            return;
        }

        selectedImagePath = file.toPath();
        imagePreview.setImage(new Image(file.toURI().toString(), 400, 0, true, true, true));
        show(imagePreview, true);
        show(removeImageButton, true);
    }

    @FXML
    private void handleRemoveImage() {
        selectedImagePath = null;
        imagePreview.setImage(null);
        show(imagePreview, false);
        show(removeImageButton, false);
    }

    @FXML
    private void handleSubmit() {
        String text = textArea.getText().trim();

        if (text.isEmpty()) {
            statusLabel.getStyleClass().setAll("error-label");
            statusLabel.setText("Текст поста не може бути порожнім");
            return;
        }

        String label = labelField.getText().trim();

        List<String> tags;
        try {
            tags = parseTags(tagsField.getText());
        } catch (IllegalArgumentException e) {
            statusLabel.getStyleClass().setAll("error-label");
            statusLabel.setText(e.getMessage());
            return;
        }

        submitButton.setDisable(true);
        statusLabel.getStyleClass().setAll("secondary-label");
        statusLabel.setText(editingPostId != null ? "Збереження..." : "Публікація...");

        Path imageToUpload = selectedImagePath;
        boolean creating = editingPostId == null;

        Thread.ofVirtual().start(() -> {
            try {
                PostApiService.CreatePostResult result = editingPostId != null
                        ? postApiService.updatePost(editingPostId, label, text, tags)
                        : postApiService.createPost(label, text, tags);

                if (!result.success()) {
                    Platform.runLater(() -> {
                        submitButton.setDisable(false);
                        statusLabel.getStyleClass().setAll("error-label");
                        statusLabel.setText(result.errorMessage());
                    });
                    return;
                }

                Long targetPostId = editingPostId != null ? editingPostId : result.postId();

                if (imageToUpload != null) {
                    Platform.runLater(() -> statusLabel.setText("Завантаження фото..."));
                    PostApiService.CreatePostResult mediaResult =
                            postApiService.uploadMedia(targetPostId, imageToUpload);

                    if (!mediaResult.success()) {
                        Platform.runLater(() -> {
                            submitButton.setDisable(false);
                            statusLabel.getStyleClass().setAll("error-label");
                            statusLabel.setText("Пост збережено, але фото не завантажилось: "
                                    + mediaResult.errorMessage());
                        });
                        return; // пост уже створено, тож onPublished усе одно варто викликати нижче в майбутньому,
                        // але зараз зупиняємось, щоб показати помилку користувачу
                    }
                }

                Platform.runLater(() -> {
                    submitButton.setDisable(false);
                    if (onPublished != null) {
                        onPublished.run();
                    }
                    resetForm();
                    if (creating && onPostPublished != null) {
                        onPostPublished.accept(targetPostId);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    submitButton.setDisable(false);
                    statusLabel.getStyleClass().setAll("error-label");
                    statusLabel.setText("Помилка з'єднання: " + e.getMessage());
                });
            }
        });
    }

    private List<String> parseTags(String rawTags) throws IllegalArgumentException {
        List<String> tags = new ArrayList<>();
        if (rawTags == null || rawTags.isBlank()) {
            return tags;
        }

        for (String token : rawTags.trim().split("\\s+")) {
            if (!token.startsWith("#")) {
                throw new IllegalArgumentException(
                        "Кожен тег має починатись з # (наприклад, #солодощі): \"" + token + "\"");
            }

            String tag = token.substring(1).trim();

            if (tag.isEmpty()) {
                throw new IllegalArgumentException("Тег не може складатись лише з символу #");
            }

            tags.add(tag);
        }

        return tags;
    }

    @FXML
    private void handleCancel() {
        resetForm();
        if (onCancelled != null) {
            onCancelled.run();
        }
    }

    private void resetForm() {
        labelField.clear();
        textArea.clear();
        tagsField.clear();
        selectedImagePath = null;
        imagePreview.setImage(null);
        show(imagePreview, false);
        show(removeImageButton, false);
        statusLabel.setText("");
        suggestDelay.stop();
        suggestRequest++;
        showSuggestions(List.of());
        editingPostId = null;
        if (titleLabel != null) {
            titleLabel.setText("Новий пост");
        }
        submitButton.setText("Опублікувати");
    }

    private static void show(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}