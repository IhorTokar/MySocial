package com.example.social.client.util;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableColumn;

import java.util.function.Function;

/** Створення текстових колонок TableView одним рядком. */
public final class TableColumns {

    private TableColumns() {
    }

    public static <S> TableColumn<S, String> text(String title, double prefWidth, Function<S, String> value) {
        TableColumn<S, String> column = new TableColumn<>(title);
        column.setPrefWidth(prefWidth);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }
}