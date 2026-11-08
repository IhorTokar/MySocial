package com.example.social.client.controller;

import com.example.social.client.graph.ForceLayout;
import com.example.social.client.graph.GraphView;
import com.example.social.client.service.AdminApiService;
import com.example.social.client.service.AdminApiService.CommunityInfo;
import com.example.social.client.service.ApiClient;
import com.example.social.client.util.AdminFormat;
import com.example.social.client.util.Async;
import com.example.social.shared.dto.CommunityDetectionResultDto;
import com.example.social.shared.dto.GraphDataDto;
import com.example.social.shared.dto.GraphSyncResultDto;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.LongConsumer;

/** Вкладка «Граф»: граф підписок, розфарбований за спільнотами Louvain або Leiden. */
public class AdminGraphController {

    private static final int LAYOUT_ITERATIONS = 400;
    private static final long DEFAULT_SEED = 42;   // однакова картинка при кожному відкритті

    /** Категоріальна палітра, читабельна і на світлому, і на темному фоні. */
    private static final Color[] PALETTE = {
            Color.web("#4E79A7"), Color.web("#F28E2B"), Color.web("#E15759"), Color.web("#76B7B2"),
            Color.web("#59A14F"), Color.web("#EDC948"), Color.web("#B07AA1"), Color.web("#FF9DA7"),
            Color.web("#9C755F"), Color.web("#4DC3E8")};
    private static final Color OTHER = Color.web("#9AA0A6");

    @FXML private ComboBox<String> algorithmCombo;
    @FXML private CheckBox labelsCheck;
    @FXML private TextField findField;
    @FXML private FlowPane legendPane;
    @FXML private StackPane graphHolder;
    @FXML private HBox infoBox;
    @FXML private Label infoLabel;
    @FXML private Label statusLabel;

    private final AdminApiService api = new AdminApiService(new ApiClient());
    private final GraphView graphView = new GraphView();

    private GraphDataDto graph;
    private int[][] edgeIndexes = new int[0][];
    private int[] groups = new int[0];
    private GraphDataDto.Node selectedNode;
    private LongConsumer onOpenUser = id -> { };
    private boolean loaded;

    @FXML
    private void initialize() {
        graphHolder.getChildren().add(graphView);

        algorithmCombo.getItems().setAll("Louvain", "Leiden");
        algorithmCombo.setValue("Louvain");
        algorithmCombo.valueProperty().addListener((obs, old, value) -> reload());

        labelsCheck.selectedProperty().addListener((obs, old, value) -> graphView.setShowLabels(value));

        graphView.setOnNodeSelected(this::showNodeInfo);
        graphView.setOnNodeOpened(node -> onOpenUser.accept(node.userId()));
    }

    /** «Керувати користувачем» і подвійний клік по вузлу відкривають вкладку «Користувачі». */
    public void setOnOpenUser(LongConsumer onOpenUser) {
        this.onOpenUser = onOpenUser;
    }

    /** Граф завантажується при першому відкритті вкладки, а не разом з усією адмінкою. */
    public void onShown() {
        if (!loaded) {
            loaded = true;
            reload();
        }
    }

    // ===== Обробники =====

    @FXML
    private void handleReload() {
        reload();
    }

    @FXML
    private void handleFit() {
        graphView.fitToView();
    }

    /** Та сама структура, інше випадкове зерно — інше розташування кластерів. */
    @FXML
    private void handleRelayout() {
        if (graph == null) {
            return;
        }
        long seed = new Random().nextLong();
        statusLabel.setText("Розкладка...");
        Async.run(() -> {
            double[][] positions = ForceLayout.compute(
                    graph.nodes().size(), edgeIndexes, groups, LAYOUT_ITERATIONS, seed);
            Platform.runLater(() -> {
                graphView.setPositions(positions);
                showSummary();
            });
        }, statusLabel::setText, null);
    }

    @FXML
    private void handleFind() {
        String query = findField.getText() == null ? "" : findField.getText().trim().replace("#", "");
        if (graph == null || query.isEmpty()) {
            return;
        }
        Optional<GraphDataDto.Node> match = graph.nodes().stream()
                .filter(n -> String.valueOf(n.userId()).equals(query)
                        || n.username().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
                .findFirst();
        if (match.isPresent()) {
            graphView.selectUser(match.get().userId());
        } else {
            statusLabel.setText("У графі немає «" + query + "» — можливо, потрібна синхронізація");
        }
    }

    @FXML
    private void handleOpenUser() {
        if (selectedNode != null) {
            onOpenUser.accept(selectedNode.userId());
        }
    }

    @FXML
    private void handleSync() {
        statusLabel.setText("Синхронізація...");
        Async.run(() -> {
            GraphSyncResultDto result = api.syncGraph();
            Platform.runLater(() -> {
                reload();
                statusLabel.setText("Синхронізовано: " + result.syncedUsers() + " користувачів, "
                        + result.syncedRelations() + " підписок. Нові вузли без спільноти — сірі, "
                        + "доки спільноти не перераховано.");
            });
        }, statusLabel::setText, null);
    }

    @FXML
    private void handleDetect() {
        boolean leiden = isLeiden();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Перерахувати спільноти алгоритмом " + algorithmCombo.getValue() + "?\n"
                        + "Номери спільнот і показники ехо-камер зміняться.");
        confirm.setHeaderText(null);
        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) {
            return;
        }
        statusLabel.setText("Пошук спільнот...");
        Async.run(() -> {
            CommunityDetectionResultDto result = api.detectCommunities(leiden);
            Platform.runLater(() -> {
                reload();
                statusLabel.setText("Знайдено спільнот: " + result.communityCount() + ", модулярність "
                        + AdminFormat.weight(result.modularity()) + ".");
            });
        }, statusLabel::setText, null);
    }

    // ===== Завантаження =====

    private void reload() {
        boolean leiden = isLeiden();
        statusLabel.setText("Завантаження графа...");
        infoBox.setVisible(false);
        infoBox.setManaged(false);

        Async.run(() -> {
            GraphDataDto data = api.getGraph(leiden);

            // Індекси замість ID: розкладка й малювання працюють з масивами
            Map<Long, Integer> indexById = new HashMap<>();
            for (int i = 0; i < data.nodes().size(); i++) {
                indexById.put(data.nodes().get(i).userId(), i);
            }
            List<int[]> edgeList = new ArrayList<>();
            for (GraphDataDto.Edge e : data.edges()) {
                Integer a = indexById.get(e.source());
                Integer b = indexById.get(e.target());
                if (a != null && b != null) {
                    edgeList.add(new int[]{a, b});
                }
            }
            int[][] edges = edgeList.toArray(new int[0][]);

            // Спільноти від найбільшої: найбільша отримує перший колір палітри
            Map<Long, Integer> sizes = new HashMap<>();
            for (GraphDataDto.Node n : data.nodes()) {
                if (n.community() != null) {
                    sizes.merge(n.community(), 1, Integer::sum);
                }
            }
            Map<Long, Integer> rank = new LinkedHashMap<>();
            sizes.entrySet().stream()
                    .sorted(Map.Entry.<Long, Integer>comparingByValue(Comparator.reverseOrder()))
                    .forEach(entry -> rank.put(entry.getKey(), rank.size()));

            int[] nodeGroups = new int[data.nodes().size()];
            Color[] colors = new Color[data.nodes().size()];
            for (int i = 0; i < data.nodes().size(); i++) {
                Long community = data.nodes().get(i).community();
                nodeGroups[i] = community == null ? -1 : rank.get(community);
                colors[i] = colorOf(community, rank, sizes);
            }

            double[][] positions = ForceLayout.compute(
                    data.nodes().size(), edges, nodeGroups, LAYOUT_ITERATIONS, DEFAULT_SEED);

            // Ехо-показники — для легенди; якщо аналіз недоступний, граф показується без них
            List<CommunityInfo> analysis;
            try {
                analysis = api.getCommunityAnalysis(leiden);
            } catch (Exception e) {
                analysis = List.of();
            }
            List<CommunityInfo> finalAnalysis = analysis;

            Platform.runLater(() -> {
                graph = data;
                edgeIndexes = edges;
                groups = nodeGroups;
                selectedNode = null;
                graphView.setHighlightedCommunity(null);
                graphView.setGraph(data.nodes(), edges, positions, colors);
                buildLegend(rank, sizes, finalAnalysis);
                showSummary();
            });
        }, statusLabel::setText, null);
    }

    private static Color colorOf(Long community, Map<Long, Integer> rank, Map<Long, Integer> sizes) {
        if (community == null) {
            return OTHER;
        }
        int r = rank.get(community);
        return r < PALETTE.length && sizes.get(community) > 1 ? PALETTE[r] : OTHER;
    }

    // ===== Легенда й інформація =====

    /** Кружок кольору + номер, розмір і ехо-показник; клік підсвічує спільноту. */
    private void buildLegend(Map<Long, Integer> rank, Map<Long, Integer> sizes, List<CommunityInfo> analysis) {
        Map<Long, Double> echo = new HashMap<>();
        for (CommunityInfo info : analysis) {
            echo.put(info.community(), info.echoChamberScore());
        }

        legendPane.getChildren().clear();
        int others = 0;
        for (Map.Entry<Long, Integer> entry : rank.entrySet()) {
            long community = entry.getKey();
            int size = sizes.get(community);
            if (entry.getValue() >= PALETTE.length || size <= 1) {
                others += size;
                continue;
            }
            String text = "#" + community + " · " + size + " уч."
                    + (echo.containsKey(community) ? " · ехо " + AdminFormat.weight(echo.get(community)) : "");
            HBox chip = legendChip(PALETTE[entry.getValue()], text);
            chip.setOnMouseClicked(e -> toggleHighlight(chip, community));
            legendPane.getChildren().add(chip);
        }
        if (others > 0) {
            legendPane.getChildren().add(legendChip(OTHER, "інші / без спільноти · " + others));
        }
    }

    private HBox legendChip(Color color, String text) {
        HBox chip = new HBox(6, new Circle(5, color), new Label(text));
        chip.getStyleClass().add("graph-legend-chip");
        chip.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return chip;
    }

    private void toggleHighlight(HBox chip, long community) {
        boolean same = Long.valueOf(community).equals(graphView.getHighlightedCommunity());
        legendPane.getChildren().forEach(node -> node.getStyleClass().remove("active"));
        if (same) {
            graphView.setHighlightedCommunity(null);
        } else {
            chip.getStyleClass().add("active");
            graphView.setHighlightedCommunity(community);
        }
    }

    private void showNodeInfo(GraphDataDto.Node node) {
        selectedNode = node;
        infoBox.setVisible(node != null);
        infoBox.setManaged(node != null);
        if (node == null) {
            return;
        }
        long following = graph.edges().stream().filter(e -> e.source() == node.userId()).count();
        long followers = graph.edges().stream().filter(e -> e.target() == node.userId()).count();
        infoLabel.setText("@" + node.username() + " · ID " + node.userId()
                + " · спільнота " + (node.community() == null ? "не визначена" : "#" + node.community())
                + "\nПідписок: " + following + " (сині стрілки) · підписників: " + followers + " (зелені)");
    }

    private void showSummary() {
        long communities = graph.nodes().stream().map(GraphDataDto.Node::community)
                .filter(c -> c != null).distinct().count();
        statusLabel.setText("Вузлів: " + graph.nodes().size() + " · ребер: " + graph.edges().size()
                + " · спільнот: " + communities + " (" + algorithmCombo.getValue() + ")"
                + " · колесо миші — масштаб, перетягування — зсув, подвійний клік — відкрити користувача");
    }

    private boolean isLeiden() {
        return "Leiden".equals(algorithmCombo.getValue());
    }
}