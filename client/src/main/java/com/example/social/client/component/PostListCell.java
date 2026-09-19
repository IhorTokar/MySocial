package com.example.social.client.component;

import com.example.social.client.service.PostApiService;
import javafx.scene.control.ListCell;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class PostListCell extends ListCell<PostApiService.PostItem> {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM, HH:mm", new Locale("uk"));

    @Override
    protected void updateItem(PostApiService.PostItem post, boolean empty) {
        super.updateItem(post, empty);

        if (empty || post == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        Label authorLabel = new Label(post.authorUsername());
        authorLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label textLabel = new Label(post.text());
        textLabel.setWrapText(true);

        Label dateLabel = new Label(formatDate(post.createdDate()));
        dateLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");

        VBox box = new VBox(4, authorLabel, textLabel, dateLabel);
        box.setStyle("-fx-padding: 8px;");

        setGraphic(box);
        setText(null);
    }

    private String formatDate(String isoDate) {
        try {
            LocalDateTime dateTime = LocalDateTime.parse(isoDate);
            return dateTime.format(DISPLAY_FORMAT);
        } catch (Exception e) {
            return isoDate;
        }
    }
}