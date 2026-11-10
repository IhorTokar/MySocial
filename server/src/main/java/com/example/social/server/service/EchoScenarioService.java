package com.example.social.server.service;

import com.example.social.server.entity.Post;
import com.example.social.server.entity.Tag;
import com.example.social.server.entity.User;
import com.example.social.server.exception.BadRequestException;
import com.example.social.server.exception.ConflictException;
import com.example.social.server.repository.FollowersRepository;
import com.example.social.server.repository.PostEmbeddingRepository;
import com.example.social.server.repository.PostRepository;
import com.example.social.server.repository.TagRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.shared.dto.CommunityAnalysisDto;
import com.example.social.shared.dto.EchoSnapshotDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Відтворюваний сценарій для звіту: як формується і як розмикається ехо-камера.
 * Працює з окремими тестовими групами echo_a1…a8 та echo_b1…b8, тож наявні дані мережі не змінюються,
 * а після експерименту все прибирається одним запитом (reset).
 *
 * Після кожного кроку автоматично робиться знімок історії ехо-камер (Louvain, з оновленням графа).
 */
@Service
public class EchoScenarioService {

    private static final Logger log = LoggerFactory.getLogger(EchoScenarioService.class);

    private static final int GROUP_SIZE = 8;
    private static final String PREFIX_A = "echo_a";
    private static final String PREFIX_B = "echo_b";
    private static final String PASSWORD = "password123";
    private static final String FOCUS_TAG = "космос";

    /** Однотемні пости групи A на кроці 2: одна тема, схожі формулювання, однаково захоплений тон. */
    private static final List<String> FOCUSED = List.of(
            "Запуск нової ракети був неймовірним! Космос — це найкраще, що є в науці.",
            "Сьогодні дивився трансляцію старту ракети. Неймовірно надихає, обожнюю космос!",
            "Космічні місії — це майбутнє людства. Кожен новий запуск — чудова новина!",
            "Нарешті успішна посадка ракети! Космос надихає мене щодня.",
            "Обожнюю новини про космос: нова ракета, нові супутники, нові відкриття!",
            "Ще один ідеальний запуск! Космічна програма просто фантастична.",
            "Космос — моя пристрасть. Кожна ракета, що злітає, робить мене щасливим.",
            "Чудовий день: успішний запуск і красиві фото Землі з орбіти. Люблю космос!");

    private record Content(String tag, String text) {
    }

    /** Різнотемні пости з різною тональністю: позитивні, негативні й нейтральні. */
    private static final List<Content> DIVERSE = List.of(
            new Content("їжа", "Приготував борщ за бабусиним рецептом — вийшло дуже смачно."),
            new Content("погода", "Знову дощ і холод, уже набридла ця сіра осінь."),
            new Content("кіно", "Фільм виявився нудним і затягнутим, шкода витраченого часу."),
            new Content("спорт", "Пробіг сьогодні десять кілометрів, почуваюся чудово."),
            new Content("книги", "Дочитав детектив: фінал передбачуваний, але загалом непогано."),
            new Content("технології", "Оновлення телефону зламало половину застосунків, дуже дратує."),
            new Content("музика", "Слухаю новий альбом улюбленого гурту — просто шедевр."),
            new Content("транспорт", "Автобус запізнився на сорок хвилин, ледве встиг на роботу."),
            new Content("подорожі", "Повернувся з Карпат, гори восени неймовірно гарні."),
            new Content("робота", "Звичайний робочий день, багато зустрічей і листів."),
            new Content("ігри", "Нова гра має чудову графіку, але сюжет слабкий."),
            new Content("садівництво", "Посадив на балконі помідори, подивимось, що виросте."));

    private static final String[] TITLES = {"",
            "Вихідний стан",
            "Формування ехо-камери",
            "Міст між групами",
            "Різноманіття контенту"};

    private static final String[] DESCRIPTIONS = {"",
            "Дві групи по 8 користувачів (echo_a1…a8, echo_b1…b8): усередині кожної всі підписані на всіх, "
                    + "у кожного по 2 пости на різні теми з різною тональністю.",
            "Група A пише лише про космос, однаково захоплено (по 3 пости на людину). Група B без змін.",
            "Кожен із групи A підписується на 6 людей із групи B, і навпаки. "
                    + "Групи зливаються в одну спільноту — теми й тональності змішуються.",
            "Група A починає писати на різні теми з різною тональністю (по 3 пости). Частка «космосу» падає."};

    private static final String[] SCREENSHOTS = {"",
            "«Історія ехо-камер»: перша точка; у таблиці спільнот — дві спільноти по 8 учасників.",
            "Таблиця спільнот: спільнота групи A зі збігом 100% і Δ echo > 0; графік у профілі echo_a1.",
            "Таблиця спільнот: одна спільнота з 16 учасників, збіг ≈ 50%, Δ echo < 0; вкладка «Граф» — групи злились.",
            "Графік у профілі echo_a1: підйом на кроці 2 і спад на кроках 3–4; «Історія ехо-камер» з усіма точками."};

    /** Стан спільноти, у якій опинився перший учасник групи. */
    public record GroupState(String group, Long community, int members, int posts,
                             double topicHomogeneity, Double semanticCohesion,
                             double sentimentHomogeneity, double echoChamberScore) {
    }

    public record StepResult(int step, String title, String description, Map<String, Integer> changes,
                             EchoSnapshotDto snapshot, GroupState groupA, GroupState groupB, String screenshot) {
    }

    private final UserService userService;
    private final UserRepository userRepository;
    private final FollowersService followersService;
    private final FollowersRepository followersRepository;
    private final PostRepository postRepository;
    private final TagRepository tagRepository;
    private final PostEmbeddingRepository postEmbeddingRepository;
    private final EmbeddingApiService embeddingApiService;
    private final PostSentimentService postSentimentService;
    private final EchoHistoryService echoHistoryService;
    private final CommunityAnalysisService communityAnalysisService;
    private final GraphSyncService graphSyncService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public EchoScenarioService(UserService userService, UserRepository userRepository,
                               FollowersService followersService, FollowersRepository followersRepository,
                               PostRepository postRepository,
                               TagRepository tagRepository, PostEmbeddingRepository postEmbeddingRepository,
                               EmbeddingApiService embeddingApiService, PostSentimentService postSentimentService,
                               EchoHistoryService echoHistoryService, CommunityAnalysisService communityAnalysisService,
                               GraphSyncService graphSyncService, JdbcTemplate jdbcTemplate,
                               TransactionTemplate transactionTemplate) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.followersService = followersService;
        this.followersRepository = followersRepository;
        this.postRepository = postRepository;
        this.tagRepository = tagRepository;
        this.postEmbeddingRepository = postEmbeddingRepository;
        this.embeddingApiService = embeddingApiService;
        this.postSentimentService = postSentimentService;
        this.echoHistoryService = echoHistoryService;
        this.communityAnalysisService = communityAnalysisService;
        this.graphSyncService = graphSyncService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    /** Виконати крок 1–4 і одразу зробити знімок ехо-камер. */
    public StepResult runStep(int step, long adminId) {
        if (step < 1 || step > 4) {
            throw new BadRequestException("Крок сценарію має бути від 1 до 4");
        }

        // зміни даних — окремою транзакцією, щоб знімок бачив їх уже збереженими
        Map<String, Integer> changes = transactionTemplate.execute(status -> switch (step) {
            case 1 -> setUp();
            case 2 -> formEchoChamber();
            case 3 -> buildBridge();
            default -> diversify();
        });

        EchoSnapshotDto snapshot = echoHistoryService.takeSnapshot(false, true, adminId);
        log.info("Echo-сценарій: крок {} «{}», знімок #{}", step, TITLES[step], snapshot.snapshotId());

        return new StepResult(step, TITLES[step], DESCRIPTIONS[step], changes, snapshot,
                groupState("A", PREFIX_A), groupState("B", PREFIX_B), SCREENSHOTS[step]);
    }

    /** Видалити тестові групи разом з їхніми постами й підписками. Знімки історії лишаються. */
    public Map<String, Integer> reset() {
        String[] names = allNames().toArray(new String[0]);
        // каскад у БД видаляє пости, підписки, лайки й коментарі цих користувачів
        int deleted = jdbcTemplate.update("DELETE FROM \"user\" WHERE username = ANY(?)",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("varchar", names)));
        graphSyncService.syncAll();   // прибрати їх і з Neo4j
        log.info("Echo-сценарій скинуто: видалено {} користувачів", deleted);
        return Map.of("usersDeleted", deleted);
    }

    // ===== Кроки =====

    private Map<String, Integer> setUp() {
        if (!group(PREFIX_A).isEmpty() || !group(PREFIX_B).isEmpty()) {
            throw new ConflictException("Сценарій уже запущено — спершу DELETE /api/dev/echo-scenario");
        }
        List<User> a = createGroup(PREFIX_A);
        List<User> b = createGroup(PREFIX_B);
        int follows = followEachOther(a) + followEachOther(b);

        Counter posts = new Counter();
        for (int i = 0; i < GROUP_SIZE; i++) {
            for (int k = 0; k < 2; k++) {
                createPost(a.get(i), DIVERSE.get((2 * i + k) % DIVERSE.size()), posts);
                createPost(b.get(i), DIVERSE.get((2 * i + k + 5) % DIVERSE.size()), posts);
            }
        }
        return posts.summary(Map.of("usersCreated", a.size() + b.size(), "followsCreated", follows));
    }

    private Map<String, Integer> formEchoChamber() {
        List<User> a = requireGroup(PREFIX_A);
        Counter posts = new Counter();
        for (int i = 0; i < GROUP_SIZE; i++) {
            for (int k = 0; k < 3; k++) {
                createPost(a.get(i), new Content(FOCUS_TAG, FOCUSED.get((3 * i + k) % FOCUSED.size())), posts);
            }
        }
        return posts.summary(Map.of());
    }

    private Map<String, Integer> buildBridge() {
        List<User> a = requireGroup(PREFIX_A);
        List<User> b = requireGroup(PREFIX_B);
        int follows = 0;
        for (int i = 0; i < GROUP_SIZE; i++) {
            for (int d = 0; d < 6; d++) {
                int j = (i + d) % GROUP_SIZE;
                follows += follow(a.get(i), b.get(j)) + follow(b.get(j), a.get(i));
            }
        }
        return Map.of("followsCreated", follows);
    }

    private Map<String, Integer> diversify() {
        List<User> a = requireGroup(PREFIX_A);
        Counter posts = new Counter();
        for (int i = 0; i < GROUP_SIZE; i++) {
            for (int k = 0; k < 3; k++) {
                createPost(a.get(i), DIVERSE.get((3 * i + k + 7) % DIVERSE.size()), posts);
            }
        }
        return posts.summary(Map.of());
    }

    // ===== Допоміжне =====

    private List<User> createGroup(String prefix) {
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= GROUP_SIZE; i++) {
            users.add(userService.registerUser(prefix + i, prefix + i + "@scenario.local", PASSWORD));
        }
        return users;
    }

    private int followEachOther(List<User> group) {
        int created = 0;
        for (User follower : group) {
            for (User following : group) {
                if (follower != following) {
                    created += follow(follower, following);
                }
            }
        }
        return created;
    }

    /**
     * @return 1 — підписку створено, 0 — вже існувала.
     * Наявність перевіряється заздалегідь: виняток з @Transactional-методу позначив би
     * всю транзакцію кроку на відкат, навіть якби ми його перехопили.
     */
    private int follow(User follower, User following) {
        if (followersRepository.findByFollowerAndFollowing(follower, following).isPresent()) {
            return 0;
        }
        followersService.follow(follower.getUserId(), following.getUserId());
        return 1;
    }

    private void createPost(User author, Content content, Counter counter) {
        Post post = new Post();
        post.setUser(author);
        post.setLabel("Сценарій: " + content.tag());
        post.setText(content.text());
        post.setTags(new HashSet<>(Set.of(tag(content.tag()))));
        postSentimentService.apply(post);
        Post saved = postRepository.saveAndFlush(post);

        List<Double> embedding = embeddingApiService.embed(content.text());
        if (embedding != null) {
            postEmbeddingRepository.saveEmbedding(saved.getPostId(), embedding);
        } else {
            counter.embeddingsMissing++;
        }
        counter.posts++;
    }

    private Tag tag(String name) {
        return tagRepository.findByName(name).orElseGet(() -> {
            Tag t = new Tag();
            t.setName(name);
            return tagRepository.save(t);
        });
    }

    private GroupState groupState(String name, String prefix) {
        List<User> users = group(prefix);
        if (users.isEmpty()) {
            return null;
        }
        Optional<CommunityAnalysisDto> community =
                communityAnalysisService.getCommunityForUser(users.get(0).getUserId(), false);
        return community
                .map(c -> new GroupState(name, c.getCommunity(), c.getMemberCount(), c.getPostCount(),
                        c.getTopicHomogeneity(), c.getSemanticCohesion(),
                        c.getSentimentHomogeneity(), c.getEchoChamberScore()))
                .orElse(new GroupState(name, null, 0, 0, 0, null, 0, 0));
    }

    private List<User> requireGroup(String prefix) {
        List<User> users = group(prefix);
        if (users.size() != GROUP_SIZE) {
            throw new ConflictException("Тестових груп немає — спершу крок 1: POST /api/dev/echo-scenario/1");
        }
        return users;
    }

    /** Учасники групи в порядку prefix1…prefix8 (лише ті, що існують). */
    private List<User> group(String prefix) {
        return IntStream.rangeClosed(1, GROUP_SIZE)
                .mapToObj(i -> userRepository.findByUsername(prefix + i))
                .flatMap(Optional::stream)
                .toList();
    }

    private static List<String> allNames() {
        List<String> names = new ArrayList<>();
        for (String prefix : List.of(PREFIX_A, PREFIX_B)) {
            for (int i = 1; i <= GROUP_SIZE; i++) {
                names.add(prefix + i);
            }
        }
        return names;
    }

    /** Лічильник створених постів і постів, для яких NLP-сервіс не повернув ембединг. */
    private static final class Counter {
        int posts;
        int embeddingsMissing;

        Map<String, Integer> summary(Map<String, Integer> other) {
            Map<String, Integer> result = new LinkedHashMap<>(other);
            result.put("postsCreated", posts);
            if (embeddingsMissing > 0) {
                result.put("embeddingsMissing", embeddingsMissing);
            }
            return result;
        }
    }
}