package com.example.social.server.service;

import com.example.social.server.entity.Post;
import com.example.social.server.entity.Tag;
import com.example.social.server.entity.User;
import com.example.social.server.repository.PostEmbeddingRepository;
import com.example.social.server.repository.PostRepository;
import com.example.social.server.repository.TagRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.shared.dto.CommunityAnalysisDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Синтетичний експеримент із закладеною структурою: перевіряє, чи упорядковує
 * echoChamberScore спільноти за заздалегідь заданим рівнем тематичної однорідності.
 * Усі створені користувачі й пости видаляються після прогону.
 */
@Service
public class EchoChamberValidationService {

    private static final Logger log = LoggerFactory.getLogger(EchoChamberValidationService.class);

    private static final String PASSWORD = "password123";
    private static final String PREFIX = "exp_";
    private static final double[] LEVELS = {0.0, 0.2, 0.4, 0.6, 0.8, 1.0};
    // має збігатися з MAX_POSTS_FOR_COHESION у CommunityAnalysisService
    private static final int MAX_POSTS_PER_COMMUNITY = 60;

    static final String[] TOPICS = {"технології", "подорожі", "їжа", "спорт", "музика", "наука"};

    static final String[][] SENTENCES = {
            { // технології
                    "Нова версія операційної системи отримала швидший інтерфейс і покращений захист даних.",
                    "Розробники презентували процесор, який споживає менше енергії та краще охолоджується.",
                    "Штучний інтелект допомагає автоматизувати рутинні завдання в програмному забезпеченні.",
                    "Хмарні сервіси спрощують зберігання файлів і спільну роботу над проєктами.",
                    "Квантові комп'ютери поки що залишаються дорогими експериментальними пристроями.",
                    "Оновлення смартфонів тепер виходять частіше, а батареї служать довше.",
                    "Мережі п'ятого покоління забезпечують швидкий інтернет навіть у великих містах.",
                    "Відкритий код дозволяє спільноті розробників швидко знаходити та виправляти помилки."
            },
            { // подорожі
                    "Влітку ми поїхали в Карпати, де піднялися на гору та побачили чудові полонини.",
                    "Квитки на потяг до Львова краще купувати заздалегідь, особливо на вихідні.",
                    "Старе місто вражає вузькими вуличками, кав'ярнями та історичною архітектурою.",
                    "Для подорожі до моря ми забронювали невеликий готель недалеко від пляжу.",
                    "Найкращий маршрут пролягає вздовж річки через мальовничі села.",
                    "У поїздці варто взяти зручне взуття, воду та карту місцевості.",
                    "Нічний поїзд дозволив нам прокинутися вже в іншому місті.",
                    "Подорож на велосипедах навколо озера залишила неймовірні враження."
            },
            { // їжа
                    "Домашній борщ із пампушками з часником залишається моєю улюбленою стравою.",
                    "Для тіста потрібні борошно, яйця, трохи молока та щіпка солі.",
                    "У новому ресторані подають вареники з вишнею та ароматну каву.",
                    "Свіжі овочі з городу роблять салат особливо смачним і соковитим.",
                    "Запечена риба з лимоном і травами готується приблизно двадцять хвилин.",
                    "Шоколадний торт ми прикрасили ягодами та збитими вершками.",
                    "Гарячий суп із грибами ідеально підходить для холодного вечора.",
                    "Вранці я зазвичай готую вівсянку з медом і горіхами."
            },
            { // спорт
                    "Збірна здобула перемогу в напруженому матчі з рахунком три на два.",
                    "Щоранку я бігаю п'ять кілометрів, щоб підтримувати витривалість.",
                    "Тренер змінив схему гри, і команда почала частіше забивати голи.",
                    "Марафон зібрав тисячі учасників, які пробігли дистанцію містом.",
                    "Плавання тричі на тиждень допомагає зміцнити м'язи та покращити дихання.",
                    "У фіналі турніру тенісист подолав суперника у трьох сетах.",
                    "Баскетбольний сезон розпочався з гучної перемоги домашньої команди.",
                    "Перед змаганнями спортсмени проводять довге розтягування та розминку."
            },
            { // музика
                    "Новий альбом гурту поєднує гітарні рифи з ніжними мелодіями фортепіано.",
                    "Вчора ми були на концерті, де оркестр виконав симфонію Бетховена.",
                    "Я вчуся грати на гітарі й щодня розучую нові акорди.",
                    "Джазовий вечір у клубі подарував атмосферу імпровізації та живого звуку.",
                    "Пісня швидко потрапила до чартів завдяки запам'ятовуваному приспіву.",
                    "Скрипаль виконав складну партію без жодної помилки.",
                    "Фестиваль зібрав виконавців різних жанрів від фолку до електроніки.",
                    "Вокалістка має сильний голос і чудово тримає ритм у складних піснях."
            },
            { // наука
                    "Астрономи виявили нову планету, що обертається навколо далекої зорі.",
                    "Дослідники вивчають, як клітини відновлюються після пошкоджень.",
                    "Експеримент у лабораторії підтвердив гіпотезу про поведінку елементарних частинок.",
                    "Зміна клімату змушує вчених переглядати прогнози щодо рівня океану.",
                    "Нова стаття описує метод вимірювання швидкості хімічних реакцій.",
                    "Біологи спостерігають за міграцією птахів за допомогою невеликих датчиків.",
                    "Теорія відносності пояснює, як гравітація викривляє простір і час.",
                    "Науковці розробили матеріал, який проводить електрику без втрат."
            }
    };

    private final UserService userService;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final TagRepository tagRepository;
    private final EmbeddingApiService embeddingApiService;
    private final PostEmbeddingRepository postEmbeddingRepository;
    private final CommunityAnalysisService communityAnalysisService;

    public EchoChamberValidationService(UserService userService,
                                        UserRepository userRepository,
                                        PostRepository postRepository,
                                        TagRepository tagRepository,
                                        EmbeddingApiService embeddingApiService,
                                        PostEmbeddingRepository postEmbeddingRepository,
                                        CommunityAnalysisService communityAnalysisService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.tagRepository = tagRepository;
        this.embeddingApiService = embeddingApiService;
        this.postEmbeddingRepository = postEmbeddingRepository;
        this.communityAnalysisService = communityAnalysisService;
    }

    private record CommunityResult(int index, double level, int dominantTopic, double dominantShare,
                                   int posts, double topic, Double semantic, double avgSentiment,
                                   double sentimentHom, double score) {
    }

    public Map<String, Object> run(int repeats, int usersPerCommunity, int postsPerUser, long seed) {
        if (repeats < 1 || repeats > 10) {
            throw new IllegalArgumentException("repeats має бути в межах 1–10");
        }
        if (usersPerCommunity < 2 || postsPerUser < 1
                || usersPerCommunity * postsPerUser > MAX_POSTS_PER_COMMUNITY) {
            throw new IllegalArgumentException("Потрібно: usersPerCommunity >= 2 і usersPerCommunity * postsPerUser <= "
                    + MAX_POSTS_PER_COMMUNITY + " (обмеження вибірки в аналізі згуртованості)");
        }
        if (embeddingApiService.embed("Перевірка зʼєднання з NLP-сервісом.") == null) {
            throw new IllegalStateException("NLP-сервіс недоступний — запустіть його (docker compose up -d)");
        }

        long started = System.nanoTime();
        Random random = new Random(seed);
        String runId = Long.toString(System.currentTimeMillis());
        Map<String, Tag> tags = ensureTags();

        List<Long> createdUserIds = new ArrayList<>();
        List<CommunityResult> results = new ArrayList<>();
        int embedFailures = 0;
        int userCounter = 0;
        int usersDeleted = 0;
        int deleteFailures = 0;

        try {
            for (int repeat = 0; repeat < repeats; repeat++) {
                for (int levelIdx = 0; levelIdx < LEVELS.length; levelIdx++) {
                    double level = LEVELS[levelIdx];
                    int dominant = (levelIdx + repeat) % TOPICS.length; // щоб тема не збігалась з рівнем
                    int communityIndex = repeat * LEVELS.length + levelIdx;

                    List<Long> members = new ArrayList<>();
                    for (int u = 0; u < usersPerCommunity; u++) {
                        String username = PREFIX + runId + "_" + (++userCounter);
                        User user = userService.registerUser(username, username + "@example.com", PASSWORD);
                        createdUserIds.add(user.getUserId());
                        members.add(user.getUserId());
                    }

                    int dominantPosts = 0;
                    int totalPosts = 0;
                    for (Long memberId : members) {
                        for (int p = 0; p < postsPerUser; p++) {
                            int topic = random.nextDouble() < level ? dominant : random.nextInt(TOPICS.length);
                            if (topic == dominant) {
                                dominantPosts++;
                            }
                            if (!savePost(memberId, topic, buildText(topic, random), tags)) {
                                embedFailures++;
                            }
                            totalPosts++;
                        }
                    }

                    CommunityAnalysisDto dto = communityAnalysisService.analyzeUsers(communityIndex, members);
                    results.add(new CommunityResult(
                            communityIndex, level, dominant, (double) dominantPosts / totalPosts, totalPosts,
                            dto.getTopicHomogeneity(), dto.getSemanticCohesion(), dto.getAvgSentiment(),
                            dto.getSentimentHomogeneity(), dto.getEchoChamberScore()));

                    log.info("Validation community {}/{} level={} score={}", communityIndex + 1,
                            repeats * LEVELS.length, level, dto.getEchoChamberScore());
                }
            }
        } finally {
            for (Long id : createdUserIds) {
                try {
                    userService.deleteAccount(id, PASSWORD);
                    usersDeleted++;
                } catch (Exception e) {
                    deleteFailures++;
                    log.warn("Could not delete experiment user {}: {}", id, e.getMessage());
                }
            }
        }

        return buildReport(results, repeats, usersPerCommunity, postsPerUser, seed,
                embedFailures, usersDeleted, deleteFailures, (System.nanoTime() - started) / 1_000_000_000.0);
    }

    /** Прибирає користувачів exp_*, якщо попередній прогін було перервано. */
    public Map<String, Object> cleanupLeftovers() {
        int deleted = 0;
        int failed = 0;
        for (User user : userRepository.findAll()) {
            if (user.getUsername() != null && user.getUsername().startsWith(PREFIX)) {
                try {
                    userService.deleteAccount(user.getUserId(), PASSWORD);
                    deleted++;
                } catch (Exception e) {
                    failed++;
                }
            }
        }
        return Map.of("deleted", deleted, "failed", failed);
    }

    // ===== Генерація даних =====

    private Map<String, Tag> ensureTags() {
        Map<String, Tag> map = new HashMap<>();
        for (String name : TOPICS) {
            Tag tag = tagRepository.findByName(name).orElseGet(() -> {
                Tag t = new Tag();
                t.setName(name);
                return tagRepository.save(t);
            });
            map.put(name, tag);
        }
        return map;
    }

    static String buildText(int topic, Random random) {
        String[] pool = SENTENCES[topic];
        int first = random.nextInt(pool.length);
        int second = random.nextInt(pool.length - 1);
        if (second >= first) {
            second++;
        }
        return pool[first] + " " + pool[second];
    }

    private boolean savePost(Long userId, int topic, String text, Map<String, Tag> tags) {
        Post post = new Post();
        post.setUser(userRepository.getReferenceById(userId));
        post.setLabel("Експериментальний пост");
        post.setText(text);
        post.setTags(new HashSet<>(List.of(tags.get(TOPICS[topic]))));
        Post saved = postRepository.save(post);

        List<Double> vector = embeddingApiService.embed(text);
        if (vector == null) {
            return false;
        }
        postEmbeddingRepository.saveEmbedding(saved.getPostId(), vector);
        return true;
    }

    // ===== Звіт =====

    private Map<String, Object> buildReport(List<CommunityResult> results, int repeats, int usersPerCommunity,
                                            int postsPerUser, long seed, int embedFailures,
                                            int usersDeleted, int deleteFailures, double seconds) {
        Map<String, Object> out = new LinkedHashMap<>();

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("repeats", repeats);
        config.put("usersPerCommunity", usersPerCommunity);
        config.put("postsPerUser", postsPerUser);
        config.put("seed", seed);
        config.put("levels", Arrays.stream(LEVELS).boxed().toList());
        config.put("topics", List.of(TOPICS));
        out.put("config", config);

        out.put("communities", results.stream().map(this::toRow).toList());

        List<Map<String, Object>> byLevel = new ArrayList<>();
        for (double level : LEVELS) {
            List<CommunityResult> group = results.stream().filter(r -> r.level() == level).toList();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("level", level);
            row.put("n", group.size());
            putMeanSd(row, "topic", group.stream().map(CommunityResult::topic).toList());
            putMeanSd(row, "semantic", group.stream().map(CommunityResult::semantic).toList());
            putMeanSd(row, "sentimentHomogeneity", group.stream().map(CommunityResult::sentimentHom).toList());
            putMeanSd(row, "score", group.stream().map(CommunityResult::score).toList());
            byLevel.add(row);
        }
        out.put("byLevel", byLevel);

        List<Double> levels = results.stream().map(CommunityResult::level).toList();
        List<Double> shares = results.stream().map(CommunityResult::dominantShare).toList();
        out.put("spearmanVsLevel", correlations(levels, results));
        out.put("spearmanVsDominantShare", correlations(shares, results));

        out.put("embedFailures", embedFailures);
        out.put("cleanup", Map.of("usersDeleted", usersDeleted, "failed", deleteFailures));
        out.put("durationSeconds", r4(seconds));
        return out;
    }

    private Map<String, Object> toRow(CommunityResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("index", r.index());
        m.put("level", r.level());
        m.put("dominantTopic", TOPICS[r.dominantTopic()]);
        m.put("dominantShare", r4(r.dominantShare()));
        m.put("posts", r.posts());
        m.put("topicHomogeneity", r4(r.topic()));
        m.put("semanticCohesion", r.semantic() == null ? null : r4(r.semantic()));
        m.put("avgSentiment", r4(r.avgSentiment()));
        m.put("sentimentHomogeneity", r4(r.sentimentHom()));
        m.put("echoChamberScore", r4(r.score()));
        return m;
    }

    private Map<String, Object> correlations(List<Double> xs, List<CommunityResult> rs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("topicHomogeneity", spearman(xs, rs.stream().map(CommunityResult::topic).toList()));
        m.put("semanticCohesion", spearman(xs, rs.stream().map(CommunityResult::semantic).toList()));
        m.put("sentimentHomogeneity", spearman(xs, rs.stream().map(CommunityResult::sentimentHom).toList()));
        m.put("composite", spearman(xs, rs.stream().map(CommunityResult::score).toList()));
        return m;
    }

    private Double spearman(List<Double> xs, List<Double> ys) {
        List<Double> x = new ArrayList<>();
        List<Double> y = new ArrayList<>();
        for (int i = 0; i < xs.size(); i++) {
            if (xs.get(i) != null && ys.get(i) != null) {
                x.add(xs.get(i));
                y.add(ys.get(i));
            }
        }
        if (x.size() < 3) {
            return null;
        }
        double rho = SpearmanCorrelation.compute(
                x.stream().mapToDouble(Double::doubleValue).toArray(),
                y.stream().mapToDouble(Double::doubleValue).toArray());
        return Double.isNaN(rho) ? null : r4(rho);
    }

    private void putMeanSd(Map<String, Object> row, String key, List<Double> values) {
        List<Double> v = values.stream().filter(Objects::nonNull).toList();
        if (v.isEmpty()) {
            row.put(key + "Mean", null);
            row.put(key + "Sd", null);
            return;
        }
        double mean = v.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double sd = v.size() < 2 ? 0 : Math.sqrt(v.stream()
                .mapToDouble(d -> (d - mean) * (d - mean)).sum() / (v.size() - 1));
        row.put(key + "Mean", r4(mean));
        row.put(key + "Sd", r4(sd));
    }

    private static double r4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}