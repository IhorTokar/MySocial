package com.example.social.client.controller;

import com.example.social.client.service.AdminApiService;
import com.example.social.client.service.AdminApiService.AdminApiException;
import com.example.social.client.service.AdminApiService.AlgorithmQuality;
import com.example.social.client.service.AdminApiService.CommunityDetail;
import com.example.social.client.service.AdminApiService.CommunityQuality;
import com.example.social.client.service.AdminApiService.CommunityStructure;
import com.example.social.client.service.AdminApiService.Matrix;
import com.example.social.client.service.AdminApiService.Person;
import com.example.social.client.service.ApiClient;
import com.example.social.client.util.AdminFormat;
import com.example.social.client.util.Async;
import com.example.social.client.util.TableColumns;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongConsumer;

/**
 * Вкладка «Спільноти»: якість виявлення порівняно зі спільнотами генератора (NMI, ARI),
 * структура знайдених спільнот (щільність, провідність, теми), лідери за PageRank,
 * «мости» за коефіцієнтом участі та матриця підписок між спільнотами.
 */
public class AdminCommunitiesController {

    /** Скільки найбільших спільнот описати докладно; решта — разом як «інші» в матриці. */
    private static final int TOP = 10;
    private static final int CONFLICT = 409;
    /** Колір акценту (-fx-accent) для теплокарти: rgba(29,155,240, частка). */
    private static final String HEAT = "rgba(29,155,240,%.3f)";
    /** Висота рядка таблиць і запас на заголовок: таблиці не тягнуться порожніми рядками й не прокручуються. */
    private static final double ROW = 26;
    private static final double TABLE_HEADER = 34;
    /** У вибраної спільноти показано 3 лідери й 3 «мости» (сервер: LEADERS, BRIDGES). */
    private static final int PEOPLE_ROWS = 3;

    @FXML private ComboBox<String> algorithmCombo;
    @FXML private Label summaryLabel;
    @FXML private Button refreshButton;
    @FXML private Button redetectButton;
    @FXML private Label qualityLabel;
    @FXML private TableView<CommunityDetail> communityTable;
    @FXML private Label matrixTitle;
    @FXML private ScrollPane matrixScroll;
    @FXML private GridPane matrixGrid;
    @FXML private Label detailTitle;
    @FXML private Label detailInfo;
    @FXML private TableView<Person> leaderTable;
    @FXML private TableView<Person> bridgeTable;
    @FXML private TableView<Person> networkBridgeTable;
    @FXML private Label statusLabel;

    private final AdminApiService api = new AdminApiService(new ApiClient());
    private LongConsumer onOpenUser = id -> { };
    private CommunityStructure structure;
    private boolean loaded;

    @FXML
    private void initialize() {
        algorithmCombo.getItems().setAll("Louvain", "Leiden");
        algorithmCombo.setValue("Louvain");
        algorithmCombo.valueProperty().addListener((obs, old, value) -> load(false));

        communityTable.getColumns().setAll(List.of(
                TableColumns.<CommunityDetail>text("Спільнота", 85, c -> "#" + c.community()),
                TableColumns.<CommunityDetail>text("Учасн.", 60, c -> String.valueOf(c.size())),
                TableColumns.<CommunityDetail>text("Теми", 190, c -> String.join(", ", c.topTags())),
                TableColumns.<CommunityDetail>text("Щільність", 75, c -> AdminFormat.score(c.density())),
                TableColumns.<CommunityDetail>text("Провідність", 85, c -> AdminFormat.score(c.conductance())),
                TableColumns.<CommunityDetail>text("Підписок всер./назовні", 140,
                        c -> c.internalFollows() + " / " + c.externalFollows()),
                TableColumns.<CommunityDetail>text("Спільнота генератора", 140, c -> c.seedCommunity() == null
                        ? "—"
                        : "#" + c.seedCommunity().community() + " (" + AdminFormat.percent(c.seedCommunity().share()) + ")")));
        communityTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        communityTable.setPlaceholder(new Label("Спільноти ще не визначені — натисніть «Перевизначити спільноти»"));
        communityTable.getSelectionModel().selectedItemProperty().addListener((obs, old, c) -> showDetail(c));
        communityTable.setFixedCellSize(ROW);
        fitRows(communityTable, 4);

        leaderTable.getColumns().setAll(List.of(
                userColumn(),
                TableColumns.<Person>text("PageRank", 80, p -> number(p.pageRank(), "%.2f")),
                TableColumns.<Person>text("Читачів", 70, p -> p.followers() == null ? "—" : String.valueOf(p.followers()))));
        bridgeTable.getColumns().setAll(bridgeColumns(false));
        networkBridgeTable.getColumns().setAll(bridgeColumns(true));

        for (TableView<Person> table : List.of(leaderTable, bridgeTable, networkBridgeTable)) {
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            table.setRowFactory(t -> openOnDoubleClick());
            table.setFixedCellSize(ROW);
        }
        fitRows(leaderTable, PEOPLE_ROWS);
        fitRows(bridgeTable, PEOPLE_ROWS);
        leaderTable.setPlaceholder(new Label("Виберіть спільноту зліва"));
        bridgeTable.setPlaceholder(new Label("Немає учасників зі зв'язками в кількох спільнотах"));
        networkBridgeTable.setPlaceholder(new Label("—"));
    }

    /** Подвійний клік по лідеру чи «мосту» відкриває користувача на вкладці «Користувачі». */
    public void setOnOpenUser(LongConsumer onOpenUser) {
        this.onOpenUser = onOpenUser;
    }

    /** Дані завантажуються при першому відкритті вкладки. */
    public void onShown() {
        if (!loaded) {
            loaded = true;
            load(false);
        }
    }

    @FXML
    private void handleRefresh() {
        load(false);
    }

    @FXML
    private void handleRedetect() {
        load(true);
    }

    // ===== Завантаження =====

    /** @param refresh спершу синхронізувати граф і заново знайти спільноти вибраним алгоритмом */
    private void load(boolean refresh) {
        String algorithm = algorithmCombo.getValue();
        boolean leiden = "Leiden".equals(algorithm);
        setBusy(true);
        statusLabel.setText(refresh
                ? "Синхронізую граф і шукаю спільноти " + algorithm + " — це може зайняти кілька секунд..."
                : "Завантаження...");
        Async.run(() -> {
            CommunityStructure loadedStructure = null;
            String structureMessage = null;
            try {
                loadedStructure = api.getCommunityStructure(leiden, TOP, refresh);
            } catch (AdminApiException e) {
                if (e.getStatus() != CONFLICT) {
                    throw e;
                }
                structureMessage = "Спільноти " + algorithm
                        + " ще не визначені — натисніть «Перевизначити спільноти».";
            }

            CommunityQuality quality = null;
            String qualityMessage = null;
            try {
                quality = api.getCommunityQuality(false);
            } catch (AdminApiException e) {
                qualityMessage = e.getStatus() == CONFLICT
                        ? "Якість виявлення: оцінка недоступна — у мережі немає згенерованих користувачів "
                        + "(спільнот, з якими можна порівняти; див. tools/report/seed.py)."
                        : "Якість виявлення: " + e.getMessage();
            }

            CommunityStructure s = loadedStructure;
            CommunityQuality q = quality;
            String sMessage = structureMessage;
            String qMessage = qualityMessage;
            Platform.runLater(() -> {
                show(s);
                qualityLabel.setText(q != null ? quality(q) : qMessage);
                statusLabel.setText(sMessage != null ? sMessage
                        : "Подвійний клік по користувачу відкриває його на вкладці «Користувачі».");
            });
        }, statusLabel::setText, () -> setBusy(false));
    }

    private void show(CommunityStructure s) {
        structure = s;
        if (s == null) {
            summaryLabel.setText("");
            communityTable.getItems().clear();
            networkBridgeTable.getItems().clear();
            matrixGrid.getChildren().clear();
            showDetail(null);
            return;
        }
        summaryLabel.setText(String.format(Locale.ROOT,
                "Спільнот: %d%s · користувачів %d · модульність %.3f · провідність %.3f · незв'язних %d",
                s.communities(), s.singletons() > 0 ? " (+" + s.singletons() + " поодиноких)" : "",
                s.users(), s.modularity(), s.weightedConductance(), s.disconnectedCommunities()));
        communityTable.getItems().setAll(s.details());
        fitRows(communityTable, Math.max(4, s.details().size()));
        networkBridgeTable.getItems().setAll(s.bridges());
        if (!s.details().isEmpty()) {
            communityTable.getSelectionModel().select(0);
        } else {
            showDetail(null);
        }
    }

    private void showDetail(CommunityDetail c) {
        if (c == null) {
            detailTitle.setText("Спільнота");
            detailInfo.setText("");
            leaderTable.getItems().clear();
            bridgeTable.getItems().clear();
        } else {
            detailTitle.setText("Спільнота #" + c.community() + " · " + c.size() + " учасн.");
            detailInfo.setText(String.format(Locale.ROOT,
                    "Щільність %.3f — є %s можливих підписок усередині; провідність %.3f — %s зв'язків веде назовні.%s",
                    c.density(), AdminFormat.percent(c.density()), c.conductance(), AdminFormat.percent(c.conductance()),
                    c.topTags().isEmpty() ? "" : " Теми: " + String.join(", ", c.topTags()) + "."));
            leaderTable.getItems().setAll(c.leaders());
            bridgeTable.getItems().setAll(c.bridges());
        }
        showMatrix(c == null ? null : "#" + c.community());
    }

    // ===== Матриця підписок =====

    /**
     * Теплокарта: рядок — звідки підписка, стовпець — на кого; у клітинці частка підписок рядка.
     * Насиченість — та сама частка; діагональ — підписки всередині спільноти.
     *
     * @param selected заголовок вибраної спільноти (виділяється жирним) або null
     */
    private void showMatrix(String selected) {
        matrixGrid.getChildren().clear();
        Matrix m = structure == null ? null : structure.matrix();
        if (m == null || m.labels().isEmpty()) {
            return;
        }
        List<String> labels = new ArrayList<>();
        m.labels().forEach(l -> labels.add(isNumber(l) ? "#" + l : l));
        // без рядків і стовпців, де немає жодної підписки (напр. «інші» — лише поодинокі користувачі)
        List<Integer> shown = new ArrayList<>();
        for (int k = 0; k < labels.size(); k++) {
            int index = k;
            int out = m.follows().get(k).stream().mapToInt(Integer::intValue).sum();
            int in = m.follows().stream().mapToInt(row -> row.get(index)).sum();
            if (out + in > 0) {
                shown.add(k);
            }
        }
        int n = shown.size();

        matrixGrid.add(header("з \\ до", false), 0, 0);
        for (int c = 0; c < n; c++) {
            String label = labels.get(shown.get(c));
            matrixGrid.add(header(label, label.equals(selected)), c + 1, 0);
        }
        matrixGrid.add(header("усього", false), n + 1, 0);

        int total = 0;
        int diagonal = 0;
        for (int r = 0; r < n; r++) {
            int i = shown.get(r);
            List<Integer> row = m.follows().get(i);
            int rowSum = row.stream().mapToInt(Integer::intValue).sum();
            total += rowSum;
            diagonal += row.get(i);
            matrixGrid.add(header(labels.get(i), labels.get(i).equals(selected)), 0, r + 1);
            for (int c = 0; c < n; c++) {
                int j = shown.get(c);
                int count = row.get(j);
                double share = rowSum == 0 ? 0 : count / (double) rowSum;
                matrixGrid.add(cell(labels.get(i), labels.get(j), count, share), c + 1, r + 1);
            }
            Label sum = header(String.valueOf(rowSum), false);
            sum.getStyleClass().setAll("secondary-label");
            matrixGrid.add(sum, n + 1, r + 1);
        }
        matrixTitle.setText(total == 0 ? "Підписки між спільнотами"
                : String.format(Locale.ROOT, "Підписки між спільнотами — %s усіх підписок усередині своєї спільноти",
                AdminFormat.percent(diagonal / (double) total)));
    }

    private static Label cell(String from, String to, int count, double share) {
        Label label = new Label(count == 0 ? "·" : AdminFormat.percent(share));
        label.setMinWidth(56);
        label.setPrefWidth(56);
        label.setAlignment(Pos.CENTER);
        label.setStyle(String.format(Locale.ROOT,
                "-fx-background-color: " + HEAT + "; -fx-background-radius: 4; -fx-padding: 5 4; "
                        + "-fx-font-size: 12px; -fx-text-fill: %s;",
                count == 0 ? 0.0 : 0.08 + 0.92 * share,
                share > 0.55 ? "white" : "-fx-primary-text"));
        Tooltip tip = new Tooltip(from.equals(to)
                ? String.format(Locale.ROOT, "Усередині %s: %d підписок — %s усіх підписок її учасників",
                from, count, AdminFormat.percent(share))
                : String.format(Locale.ROOT, "З %s до %s: %d підписок — %s усіх підписок %s",
                from, to, count, AdminFormat.percent(share), from));
        tip.setShowDelay(Duration.millis(150));
        label.setTooltip(tip);
        return label;
    }

    private static Label header(String text, boolean bold) {
        Label label = new Label(text);
        label.setMinWidth(56);
        label.setAlignment(Pos.CENTER);
        label.setStyle("-fx-font-size: 12px; -fx-text-fill: -fx-primary-text;"
                + (bold ? " -fx-font-weight: bold; -fx-underline: true;" : ""));
        return label;
    }

    // ===== Допоміжне =====

    /** «Louvain — NMI 1.000 · ARI 1.000 · знайдено 8 | Leiden — ...». */
    private static String quality(CommunityQuality q) {
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT,
                "Якість виявлення на згенерованих користувачах (%d, закладено %d спільнот, модульність справжнього "
                        + "розбиття %.3f): ", q.users(), q.trueCommunities(), q.trueModularity()));
        List<String> parts = new ArrayList<>();
        for (AlgorithmQuality a : q.algorithms()) {
            String name = "leiden".equals(a.algorithm()) ? "Leiden" : "Louvain";
            parts.add(a.error() != null
                    ? name + " — " + a.error()
                    : String.format(Locale.ROOT, "%s — NMI %s · ARI %s · чистота %s · знайдено %d",
                    name, AdminFormat.score(a.nmi()), AdminFormat.score(a.ari()), AdminFormat.score(a.purity()),
                    a.foundCommunities() == null ? 0 : a.foundCommunities()));
        }
        return text.append(String.join("   |   ", parts)).toString();
    }

    private static List<javafx.scene.control.TableColumn<Person, String>> bridgeColumns(boolean withCommunity) {
        List<javafx.scene.control.TableColumn<Person, String>> columns = new ArrayList<>();
        columns.add(userColumn());
        if (withCommunity) {
            columns.add(TableColumns.<Person>text("Спільнота", 80, p -> p.community() == null ? "—" : "#" + p.community()));
        }
        columns.add(TableColumns.<Person>text("P", 55, p -> number(p.participation(), "%.3f")));
        columns.add(TableColumns.<Person>text("Зв'язків", 65, p -> p.links() == null ? "—" : String.valueOf(p.links())));
        columns.add(TableColumns.<Person>text("Спільнот", 65,
                p -> p.communitiesReached() == null ? "—" : String.valueOf(p.communitiesReached())));
        return columns;
    }

    private static javafx.scene.control.TableColumn<Person, String> userColumn() {
        return TableColumns.<Person>text("Користувач", 140,
                p -> p.username() == null ? "#" + p.userId() : "@" + p.username());
    }

    private TableRow<Person> openOnDoubleClick() {
        TableRow<Person> row = new TableRow<>();
        row.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()) {
                onOpenUser.accept(row.getItem().userId());
            }
        });
        return row;
    }

    /** Висота таблиці рівно під {@code rows} рядків. */
    private static void fitRows(TableView<?> table, int rows) {
        double height = TABLE_HEADER + rows * ROW;
        table.setPrefHeight(height);
        table.setMinHeight(height);
        table.setMaxHeight(height);
    }

    private static String number(Double value, String format) {
        return value == null ? "—" : String.format(Locale.ROOT, format, value);
    }

    private static boolean isNumber(String s) {
        return !s.isEmpty() && s.chars().allMatch(Character::isDigit);
    }

    private void setBusy(boolean busy) {
        refreshButton.setDisable(busy);
        redetectButton.setDisable(busy);
        algorithmCombo.setDisable(busy);
    }
}