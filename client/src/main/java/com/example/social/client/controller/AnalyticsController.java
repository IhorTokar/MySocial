package com.example.social.client.controller;

import com.example.social.client.component.InterestsDialog;
import com.example.social.client.service.ApiClient;
import com.example.social.client.service.InsightsApiService;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * «Моя стрічка»: аналіз спільнот, показаний самій людині. Її спільнота й наскільки вона схожа на ехо-камеру,
 * склад стрічки «Для вас» (скільки постів зі своєї спільноти порівняно з випадковою стрічкою, чому пости показано),
 * теми й настрій вподобаного порівняно з мережею, а також обрані теми (холодний старт).
 */
public class AnalyticsController {

    /** Ширина смуги для частки 100%. */
    private static final double BAR_WIDTH = 260;
    private static final double LABEL_WIDTH = 230;

    @FXML private Label statusLabel;
    @FXML private VBox sections;
    @FXML private Button refreshButton;

    private final InsightsApiService api = new InsightsApiService(new ApiClient());
    private MainShellController shell;

    public void setShell(MainShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void initialize() {
        load();
    }

    @FXML
    private void handleRefresh() {
        load();
    }

    private void load() {
        refreshButton.setDisable(true);
        statusLabel.setText("Рахуємо вашу стрічку…");
        Thread.ofVirtual().start(() -> {
            try {
                JsonNode data = api.getInsights();
                Platform.runLater(() -> {
                    refreshButton.setDisable(false);
                    statusLabel.setText("");
                    render(data);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    refreshButton.setDisable(false);
                    statusLabel.setText("Не вдалося завантажити: " + e.getMessage());
                });
            }
        });
    }

    private void render(JsonNode data) {
        List<Node> cards = new ArrayList<>();

        // висновки простими словами
        if (data.path("hints").size() > 0) {
            VBox hints = new VBox(8);
            data.path("hints").forEach(h -> hints.getChildren().add(hint(h.asText())));
            cards.add(hints);
        }
        cards.add(feedCard(data.path("feed")));
        cards.add(communityCard(data.path("community")));
        cards.add(likesCard(data.path("likes")));
        cards.add(sentimentCard(data.path("likes").path("sentiment")));
        cards.add(interestsCard(data.path("interests")));
        sections.getChildren().setAll(cards);
    }

    // ===== Розділи =====

    private Node feedCard(JsonNode feed) {
        VBox card = card("Ваша стрічка «Для вас» зараз",
                "Перші " + feed.path("size").asInt() + " постів стрічки — приблизно один екран прокручування.");
        if (feed.path("size").asInt() == 0) {
            card.getChildren().add(secondary("У стрічці поки немає постів."));
            return card;
        }
        if (feed.has("ownCommunityShare")) {
            double own = feed.path("ownCommunityShare").asDouble();
            double random = feed.path("randomOwnCommunityShare").asDouble();
            Label big = new Label(percent(own));
            big.getStyleClass().add("insight-number");
            card.getChildren().add(new HBox(10, big, secondary("постів — від людей з вашої спільноти")));
            card.getChildren().add(bar("Ваша стрічка", own, false,
                    "Частка постів авторів з вашої спільноти серед перших постів стрічки"));
            card.getChildren().add(bar("Якби пости бралися навмання", random, true,
                    "Частка постів вашої спільноти серед усіх постів мережі (крім ваших)"));
            if (random > 0) {
                card.getChildren().add(secondary(String.format(Locale.ROOT,
                        "Порівняно з випадковою стрічкою — ×%.0f.", own / random)));
            }
        } else {
            card.getChildren().add(secondary("Вашу спільноту ще не визначено (адміністратор перераховує спільноти "
                    + "в адмін-панелі), тож частку постів зі своєї спільноти порахувати не можна."));
        }
        card.getChildren().add(bar("Від тих, на кого ви підписані", feed.path("followedShare").asDouble(), false, null));
        double setting = feed.path("outsideShareSetting").asDouble();
        card.getChildren().add(secondary(String.format(Locale.ROOT,
                "Різних авторів: %d · різних тем: %d · додано з інших спільнот: %d%s",
                feed.path("distinctAuthors").asInt(), feed.path("distinctTags").asInt(),
                feed.path("otherCommunityPosts").asInt(),
                setting > 0 ? " (налаштування: " + percent(setting) + " стрічки)" : " (розбавлення вимкнене)")));

        if (feed.path("reasons").size() > 0) {
            card.getChildren().add(subheader("Чому ці пости показано"));
            for (JsonNode r : feed.path("reasons")) {
                card.getChildren().add(secondary("• " + r.path("reason").asText() + " — "
                        + posts(r.path("posts").asInt())));
            }
        }
        return card;
    }

    private Node communityCard(JsonNode c) {
        VBox card = card("Ваша спільнота",
                "Групу людей, які здебільшого підписані одне на одного, знаходить алгоритм Louvain у графі підписок.");
        if (c.isMissingNode() || c.isNull()) {
            card.getChildren().add(secondary("Спільноту ще не визначено або ви поки ні на кого не підписані."));
            return card;
        }
        card.getChildren().add(new Label("Спільнота #" + c.path("community").asLong() + " · "
                + c.path("members").asInt() + " учасників"));
        if (c.path("topTags").size() > 0) {
            card.getChildren().add(secondary("Головні теми: " + join(c.path("topTags"), "#")));
        }
        if (c.path("keywords").size() > 0) {
            card.getChildren().add(secondary("Ключові слова з текстів постів: " + join(c.path("keywords"), "")));
        }
        if (c.has("echoScore")) {
            card.getChildren().add(subheader("Наскільки спільнота схожа на ехо-камеру"));
            card.getChildren().add(bar("Ваша спільнота", c.path("echoScore").asDouble(), false,
                    "Показник ехо-камери: середнє трьох оцінок нижче; 1 — усі пишуть про одне й те саме, "
                            + "однаковими словами й з однаковим настроєм"));
            if (c.has("networkEchoScore")) {
                card.getChildren().add(bar("Мережа в середньому", c.path("networkEchoScore").asDouble(), true,
                        "Середній показник усіх спільнот з останнього знімка (більші спільноти важать більше)"));
            }
            card.getChildren().add(bar("Однорідність тем", c.path("topicHomogeneity").asDouble(), false,
                    "Наскільки пости спільноти зосереджені на кількох темах"));
            if (c.hasNonNull("semanticCohesion")) {
                card.getChildren().add(bar("Згуртованість (схожість текстів)", c.path("semanticCohesion").asDouble(),
                        false, "Середня схожість текстів постів за змістом"));
            }
            card.getChildren().add(bar("Однорідність настрою", c.path("sentimentHomogeneity").asDouble(), false,
                    "Наскільки в спільноті переважає один настрій постів"));
        }
        return card;
    }

    private Node likesCard(JsonNode likes) {
        VBox card = card("Що ви вподобуєте",
                "Теми вподобаних постів (синя смуга) порівняно з усіма вподобаннями мережі (сіра).");
        if (likes.path("count").asInt() == 0) {
            card.getChildren().add(secondary("Ви ще нічого не вподобали."));
            return card;
        }
        for (JsonNode t : likes.path("tags")) {
            card.getChildren().add(pair("#" + t.path("tag").asText(), t.path("mine").asDouble(),
                    t.path("network").asDouble()));
        }
        if (likes.hasNonNull("topicHomogeneity")) {
            card.getChildren().add(secondary(String.format(Locale.ROOT,
                    "Вподобань: %d. Особиста однорідність тем: %.2f (0 — усі теми мережі порівну, 1 — одна тема).",
                    likes.path("count").asInt(), likes.path("topicHomogeneity").asDouble())));
        }
        return card;
    }

    private Node sentimentCard(JsonNode sentiment) {
        VBox card = card("Настрій вподобаного",
                "Настрій постів визначає модель обробки тексту: позитивний, нейтральний чи негативний.");
        JsonNode mine = sentiment.path("mine");
        JsonNode network = sentiment.path("network");
        if (mine.path("posts").asDouble() == 0) {
            card.getChildren().add(secondary("Немає вподобаних постів із визначеним настроєм."));
            return card;
        }
        card.getChildren().add(pair("Позитивні", mine.path("positive").asDouble(), network.path("positive").asDouble()));
        card.getChildren().add(pair("Нейтральні", mine.path("neutral").asDouble(), network.path("neutral").asDouble()));
        card.getChildren().add(pair("Негативні", mine.path("negative").asDouble(), network.path("negative").asDouble()));
        card.getChildren().add(secondary("Синя смуга — ваші вподобання, сіра — усі пости мережі."));
        return card;
    }

    private Node interestsCard(JsonNode interests) {
        VBox card = card("Ваші теми",
                "Поки у вас немає вподобань, стрічка підбирає пости за цими темами; потім вони лишаються серед кандидатів.");
        FlowPane chips = new FlowPane(6, 6);
        interests.forEach(t -> {
            Label chip = new Label("#" + t.asText());
            chip.getStyleClass().add("post-tag");
            chips.getChildren().add(chip);
        });
        if (chips.getChildren().isEmpty()) {
            chips.getChildren().add(secondary("Теми не обрано."));
        }
        Button change = new Button("Змінити теми");
        change.getStyleClass().addAll("button", "button-secondary");
        change.setOnAction(e -> openInterests(change));
        card.getChildren().addAll(chips, change);
        return card;
    }

    private void openInterests(Button button) {
        button.setDisable(true);
        Thread.ofVirtual().start(() -> {
            try {
                InsightsApiService.Interests interests = api.getInterests();
                Platform.runLater(() -> {
                    button.setDisable(false);
                    InterestsDialog.show(sections.getScene().getWindow(), interests, this::load);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    button.setDisable(false);
                    statusLabel.setText("Не вдалося завантажити теми: " + e.getMessage());
                });
            }
        });
    }

    // ===== Будівельні блоки =====

    private static VBox card(String title, String subtitle) {
        Label header = new Label(title);
        header.getStyleClass().add("post-title");
        VBox card = new VBox(8, header, secondary(subtitle));
        card.getStyleClass().add("post-card");
        return card;
    }

    private static Label subheader(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    private static Label secondary(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("secondary-label");
        label.setWrapText(true);
        return label;
    }

    private static Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("insight-hint");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    /** Рядок «назва — смуга — відсоток»; network — сіра смуга порівняння. */
    private static HBox bar(String name, double share, boolean network, String tooltip) {
        Label label = new Label(name);
        label.setMinWidth(LABEL_WIDTH);
        label.setPrefWidth(LABEL_WIDTH);
        label.setWrapText(true);
        Label value = new Label(percent(share));
        value.setMinWidth(48);
        HBox row = new HBox(10, label, track(share, network ? "share-bar-fill-network" : "share-bar-fill", 12), value);
        row.setAlignment(Pos.CENTER_LEFT);
        if (tooltip != null) {
            Tooltip.install(row, new Tooltip(tooltip));
        }
        return row;
    }

    /** Дві смуги в одному рядку: моя (синя) і мережі (сіра). */
    private static HBox pair(String name, double mine, double network) {
        Label label = new Label(name);
        label.setMinWidth(LABEL_WIDTH);
        label.setPrefWidth(LABEL_WIDTH);
        VBox bars = new VBox(3, track(mine, "share-bar-fill", 9), track(network, "share-bar-fill-network", 9));
        Label value = new Label(percent(mine) + " / " + percent(network));
        value.getStyleClass().add("secondary-label");
        HBox row = new HBox(10, label, bars, value);
        row.setAlignment(Pos.CENTER_LEFT);
        Tooltip.install(row, new Tooltip("Ви: " + percent(mine) + " · уся мережа: " + percent(network)));
        return row;
    }

    private static StackPane track(double share, String fillClass, double height) {
        Region fill = new Region();
        fill.getStyleClass().add(fillClass);
        double width = Math.max(0, Math.min(1, share)) * BAR_WIDTH;
        fill.setMinSize(width, height);
        fill.setPrefSize(width, height);
        fill.setMaxSize(width, height);
        StackPane track = new StackPane(fill);
        track.getStyleClass().add("share-bar-track");
        track.setAlignment(Pos.CENTER_LEFT);
        track.setMinSize(BAR_WIDTH, height);
        track.setPrefSize(BAR_WIDTH, height);
        track.setMaxSize(BAR_WIDTH, height);
        HBox.setHgrow(track, Priority.NEVER);
        return track;
    }

    /** 20%, а для малих часток — з десятими (0.3%), щоб не показувати «0%» там, де щось є. */
    private static String percent(double share) {
        return share > 0 && share < 0.01
                ? String.format(Locale.ROOT, "%.1f%%", share * 100)
                : String.format(Locale.ROOT, "%.0f%%", share * 100);
    }

    private static String posts(int n) {
        int mod10 = n % 10;
        int mod100 = n % 100;
        String word = mod10 == 1 && mod100 != 11 ? "пост"
                : mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? "пости" : "постів";
        return n + " " + word;
    }

    private static String join(JsonNode array, String prefix) {
        List<String> parts = new ArrayList<>();
        array.forEach(n -> parts.add(prefix + n.asText()));
        return String.join(", ", parts);
    }
}
