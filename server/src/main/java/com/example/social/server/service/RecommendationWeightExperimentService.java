package com.example.social.server.service;

import com.example.social.server.entity.Post;
import com.example.social.server.entity.PostLike;
import com.example.social.server.entity.Tag;
import com.example.social.server.entity.User;
import com.example.social.server.repository.PostEmbeddingRepository;
import com.example.social.server.repository.PostLikeRepository;
import com.example.social.server.repository.PostRepository;
import com.example.social.server.repository.TagRepository;
import com.example.social.server.repository.UserRepository;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Офлайн-експеримент з вагами гібридної стрічки на синтетичному світі із закладеною структурою:
 * частину лайків ховаємо, будуємо стрічку без них і міряємо, чи потрапили приховані пости вгору.
 * Усі створені користувачі, пости й вузли графа видаляються після прогону.
 */
@Service
public class RecommendationWeightExperimentService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationWeightExperimentService.class);

    private static final String PASSWORD = "password123";
    private static final String PREFIX = "rec_";
    private static final int EMBEDDING_DIM = 16;
    private static final int RANDOM_DRAWS = 100;

    // припущення генератора даних (описуються у звіті)
    private static final double OWN_TOPIC_POST_PROB = 0.85;   // частка дописів користувача на його тему
    private static final double SAME_TOPIC_FOLLOW_PROB = 0.8; // частка підписок на людей зі своєї теми
    private static final double OFF_TOPIC_FACTOR = 0.15;      // відносна ймовірність лайка чужої теми
    private static final double QUALITY_SIGMA = 0.8;          // розкид «якості» постів (логнормальний)
    private static final int POST_AGE_DAYS = 14;

    public record Params(int users, int postsPerUser, int likesPerUser, int followsPerUser,
                         double hiddenShare, double recencyTauDays, long seed, int k,
                         int gridSteps, boolean includeGrid, double[] candidate) {
    }

    private record SimUser(long id, int topic) {
    }

    private record SimPost(long id, long authorId, int topic, double quality, double ageDays) {
    }

    private record Method(String name, double[] weights, double bonus) {
    }

    private final UserService userService;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final TagRepository tagRepository;
    private final PostLikeRepository postLikeRepository;
    private final FollowersService followersService;
    private final EmbeddingApiService embeddingApiService;
    private final PostEmbeddingRepository postEmbeddingRepository;
    private final GraphSyncService graphSyncService;
    private final GraphEmbeddingService graphEmbeddingService;
    private final RecommendationService recommendationService;
    private final Driver neo4jDriver;

    public RecommendationWeightExperimentService(UserService userService,
                                                 UserRepository userRepository,
                                                 PostRepository postRepository,
                                                 TagRepository tagRepository,
                                                 PostLikeRepository postLikeRepository,
                                                 FollowersService followersService,
                                                 EmbeddingApiService embeddingApiService,
                                                 PostEmbeddingRepository postEmbeddingRepository,
                                                 GraphSyncService graphSyncService,
                                                 GraphEmbeddingService graphEmbeddingService,
                                                 RecommendationService recommendationService,
                                                 Driver neo4jDriver) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.tagRepository = tagRepository;
        this.postLikeRepository = postLikeRepository;
        this.followersService = followersService;
        this.embeddingApiService = embeddingApiService;
        this.postEmbeddingRepository = postEmbeddingRepository;
        this.graphSyncService = graphSyncService;
        this.graphEmbeddingService = graphEmbeddingService;
        this.recommendationService = recommendationService;
        this.neo4jDriver = neo4jDriver;
    }

    public Map<String, Object> run(Params p) {
        validate(p);
        if (embeddingApiService.embed("Перевірка зʼєднання з NLP-сервісом.") == null) {
            throw new IllegalStateException("NLP-сервіс недоступний — запустіть його (docker compose up -d)");
        }

        long started = System.nanoTime();
        Random random = new Random(p.seed());
        String runId = Long.toString(System.currentTimeMillis());
        Map<String, Tag> tags = ensureTags();
        int topicCount = EchoChamberValidationService.TOPICS.length;

        List<Long> createdUserIds = new ArrayList<>();
        boolean graphTouched = false;
        int usersDeleted = 0;
        int deleteFailures = 0;
        boolean neo4jCleaned = false;
        String neo4jNote = "";
        Map<String, Object> report = null;

        try {
            // 1. користувачі: інтерес = i mod кількість тем
            List<SimUser> users = new ArrayList<>();
            for (int i = 0; i < p.users(); i++) {
                String username = PREFIX + runId + "_" + (i + 1);
                User u = userService.registerUser(username, username + "@example.com", PASSWORD);
                createdUserIds.add(u.getUserId());
                users.add(new SimUser(u.getUserId(), i % topicCount));
            }

            // 2. пости з тематичним текстом, «якістю» та віком
            List<SimPost> posts = new ArrayList<>();
            int embedFailures = 0;
            for (SimUser u : users) {
                for (int j = 0; j < p.postsPerUser(); j++) {
                    int topic = random.nextDouble() < OWN_TOPIC_POST_PROB ? u.topic() : random.nextInt(topicCount);
                    double quality = Math.exp(QUALITY_SIGMA * random.nextGaussian());
                    double ageDays = random.nextDouble() * POST_AGE_DAYS;
                    String text = EchoChamberValidationService.buildText(topic, random);

                    Post post = new Post();
                    post.setUser(userRepository.getReferenceById(u.id()));
                    post.setLabel("Експериментальний пост");
                    post.setText(text);
                    post.setTags(new HashSet<>(List.of(tags.get(EchoChamberValidationService.TOPICS[topic]))));
                    post.setCreatedDate(LocalDateTime.now().minusMinutes((long) (ageDays * 24 * 60)));
                    Post saved = postRepository.save(post);

                    List<Double> vector = embeddingApiService.embed(text);
                    if (vector == null) {
                        embedFailures++;
                    } else {
                        postEmbeddingRepository.saveEmbedding(saved.getPostId(), vector);
                    }
                    posts.add(new SimPost(saved.getPostId(), u.id(), topic, quality, ageDays));
                }
            }

            // 3. підписки: здебільшого на людей зі своєю темою
            int followsCreated = 0;
            for (SimUser u : users) {
                List<SimUser> sameTopic = users.stream()
                        .filter(x -> x.topic() == u.topic() && x.id() != u.id())
                        .collect(Collectors.toList());
                for (int f = 0; f < p.followsPerUser(); f++) {
                    SimUser target = random.nextDouble() < SAME_TOPIC_FOLLOW_PROB
                            ? sameTopic.get(random.nextInt(sameTopic.size()))
                            : users.get(random.nextInt(users.size()));
                    if (target.id() == u.id()) {
                        continue;
                    }
                    try {
                        followersService.follow(u.id(), target.id());
                        followsCreated++;
                    } catch (IllegalArgumentException ignored) {
                        // вже підписаний
                    }
                }
            }

            // 4. лайки: видимі зберігаємо в БД, приховані лишаються лише в пам'яті
            Map<Long, Set<Long>> visible = new HashMap<>();
            Map<Long, Set<Long>> hidden = new HashMap<>();
            int visibleLikes = 0;
            int hiddenLikes = 0;
            for (SimUser u : users) {
                List<SimPost> pool = posts.stream()
                        .filter(x -> x.authorId() != u.id())
                        .collect(Collectors.toList());
                double[] w = new double[pool.size()];
                for (int i = 0; i < w.length; i++) {
                    SimPost sp = pool.get(i);
                    double topicFactor = sp.topic() == u.topic() ? 1.0 : OFF_TOPIC_FACTOR;
                    double recency = p.recencyTauDays() > 0 ? Math.exp(-sp.ageDays() / p.recencyTauDays()) : 1.0;
                    w[i] = sp.quality() * topicFactor * recency;
                }
                List<Integer> chosen = sampleDistinct(w, Math.min(p.likesPerUser(), pool.size()), random);
                Collections.shuffle(chosen, random);
                int hiddenCount = Math.max(1, (int) Math.round(chosen.size() * p.hiddenShare()));

                Set<Long> vis = new HashSet<>();
                Set<Long> hid = new HashSet<>();
                for (int i = 0; i < chosen.size(); i++) {
                    long postId = pool.get(chosen.get(i)).id();
                    if (i < hiddenCount) {
                        hid.add(postId);
                    } else {
                        vis.add(postId);
                    }
                }
                for (long postId : vis) {
                    PostLike like = new PostLike();
                    like.setUser(userRepository.getReferenceById(u.id()));
                    like.setPost(postRepository.getReferenceById(postId));
                    postLikeRepository.save(like);
                }
                visible.put(u.id(), vis);
                hidden.put(u.id(), hid);
                visibleLikes += vis.size();
                hiddenLikes += hid.size();
            }

            // 5. граф: синхронізація та node2vec (потрібні для графової складової)
            graphTouched = true;
            graphSyncService.syncAll();
            graphEmbeddingService.generateEmbeddings(EMBEDDING_DIM);

            // 6. оцінки компонентів для кожного користувача
            Set<Long> allowed = posts.stream().map(SimPost::id).collect(Collectors.toSet());
            Random tieRandom = new Random(p.seed() + 1);
            List<WeightSearch.UserCase> cases = new ArrayList<>();
            double coverageSum = 0;
            double poolSum = 0;
            for (SimUser u : users) {
                List<RecommendationService.CandidateScores> all =
                        recommendationService.computeCandidateScores(u.id(), allowed);
                Set<Long> vis = visible.get(u.id());
                Set<Long> hid = hidden.get(u.id());
                List<RecommendationService.CandidateScores> pool = all.stream()
                        .filter(x -> x.authorId() != u.id() && !vis.contains(x.postId()))
                        .collect(Collectors.toList());

                int n = pool.size();
                double[][] scores = new double[n][WeightSearch.COMPONENTS];
                boolean[] followed = new boolean[n];
                boolean[] relevant = new boolean[n];
                double[] tie = new double[n];
                int reachable = 0;
                for (int i = 0; i < n; i++) {
                    RecommendationService.CandidateScores c = pool.get(i);
                    scores[i] = new double[]{c.popularity(), c.itemCf(), c.graphSim(), c.semantic(), c.recency()};
                    followed[i] = c.followed();
                    relevant[i] = hid.contains(c.postId());
                    if (relevant[i]) {
                        reachable++;
                    }
                    tie[i] = tieRandom.nextDouble();
                }
                cases.add(new WeightSearch.UserCase(u.id(), scores, followed, relevant, hid.size(), tie));
                coverageSum += (double) reachable / hid.size();
                poolSum += n;
            }

            // 7. розподіл користувачів: половина для підбору, половина для перевірки
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < cases.size(); i++) {
                order.add(i);
            }
            Collections.shuffle(order, new Random(p.seed() + 2));
            boolean[] isTuning = new boolean[cases.size()];
            List<WeightSearch.UserCase> tuning = new ArrayList<>();
            List<WeightSearch.UserCase> validation = new ArrayList<>();
            for (int i = 0; i < order.size(); i++) {
                int idx = order.get(i);
                if (i < order.size() / 2) {
                    isTuning[idx] = true;
                    tuning.add(cases.get(idx));
                } else {
                    validation.add(cases.get(idx));
                }
            }

            // 8. перебір сітки ваг; вибір — лише за користувачами «підбору»
            RecommendationService.Weights def = recommendationService.currentWeights();
            double[] defaultWeights = {def.popularity(), def.itemCf(), def.graphSim(), def.semantic(), def.recency()};
            double bonus = def.followedBonus();
            RecommendationService.Weights expert = RecommendationService.Weights.DEFAULT;
            double[] expertWeights = {expert.popularity(), expert.itemCf(), expert.graphSim(), expert.semantic(), expert.recency()};

            List<double[]> grid = WeightSearch.enumerateGrid(p.gridSteps());
            double[] tuneNdcg = new double[grid.size()];
            double[] valNdcg = new double[grid.size()];
            int bestIdx = -1;
            for (int g = 0; g < grid.size(); g++) {
                tuneNdcg[g] = WeightSearch.evaluateMean(tuning, grid.get(g), bonus, p.k()).ndcg();
                valNdcg[g] = WeightSearch.evaluateMean(validation, grid.get(g), bonus, p.k()).ndcg();
                if (bestIdx < 0
                        || tuneNdcg[g] > tuneNdcg[bestIdx] + 1e-12
                        || (Math.abs(tuneNdcg[g] - tuneNdcg[bestIdx]) <= 1e-12
                        && distance(grid.get(g), defaultWeights) < distance(grid.get(bestIdx), defaultWeights))) {
                    bestIdx = g;
                }
            }
            double[] best = grid.get(bestIdx);

            // 9. порівняння методів
            List<Method> methods = new ArrayList<>();
            methods.add(new Method("random", new double[5], 0.0));
            methods.add(new Method("popularityOnly", new double[]{1, 0, 0, 0, 0}, 0.0));
            methods.add(new Method("itemCfOnly", new double[]{0, 1, 0, 0, 0}, 0.0));
            methods.add(new Method("graphOnly", new double[]{0, 0, 1, 0, 0}, 0.0));
            methods.add(new Method("semanticOnly", new double[]{0, 0, 0, 1, 0}, 0.0));
            methods.add(new Method("recencyOnly", new double[]{0, 0, 0, 0, 1}, 0.0));
            methods.add(new Method("equalWeights", new double[]{0.2, 0.2, 0.2, 0.2, 0.2}, bonus));
            methods.add(new Method("default", defaultWeights, bonus));
            methods.add(new Method("tuned", best, bonus));
            methods.add(new Method("expertDefault", expertWeights, expert.followedBonus()));
            if (p.candidate() != null) {
                methods.add(new Method("candidate", p.candidate(), bonus));
            }
            String[] names = {"popularity", "itemCf", "graphSim", "semantic", "recency"};
            for (int j = 0; j < 5; j++) {
                methods.add(new Method("default_without_" + names[j], withoutComponent(defaultWeights, j), bonus));
            }

            List<Map<String, Object>> methodReports = new ArrayList<>();
            Map<String, Object> perUserNdcg = new LinkedHashMap<>();
            for (Method m : methods) {
                RankingMetrics.Metrics[] perUser = m.name().equals("random")
                        ? randomBaseline(cases, p.k(), p.seed() + 3)
                        : evaluateAll(cases, m, p.k());

                List<RankingMetrics.Metrics> tuneList = new ArrayList<>();
                List<RankingMetrics.Metrics> valList = new ArrayList<>();
                double[] ndcgs = new double[perUser.length];
                for (int i = 0; i < perUser.length; i++) {
                    (isTuning[i] ? tuneList : valList).add(perUser[i]);
                    ndcgs[i] = r4(perUser[i].ndcg());
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", m.name());
                row.put("weights", Arrays.stream(m.weights()).map(RecommendationWeightExperimentService::r4).boxed().toList());
                row.put("followedBonus", m.bonus());
                row.put("tuning", metricsMap(RankingMetrics.mean(tuneList)));
                row.put("validation", metricsMap(RankingMetrics.mean(valList)));
                methodReports.add(row);
                perUserNdcg.put(m.name(), ndcgs);
            }

            // 10. звіт
            report = new LinkedHashMap<>();
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("candidate", p.candidate() == null ? null : Arrays.stream(p.candidate()).boxed().toList());
            config.put("users", p.users());
            config.put("postsPerUser", p.postsPerUser());
            config.put("likesPerUser", p.likesPerUser());
            config.put("followsPerUser", p.followsPerUser());
            config.put("hiddenShare", p.hiddenShare());
            config.put("recencyTauDays", p.recencyTauDays());
            config.put("seed", p.seed());
            config.put("k", p.k());
            config.put("gridSteps", p.gridSteps());
            config.put("assumptions", Map.of(
                    "ownTopicPostProb", OWN_TOPIC_POST_PROB,
                    "sameTopicFollowProb", SAME_TOPIC_FOLLOW_PROB,
                    "offTopicLikeFactor", OFF_TOPIC_FACTOR,
                    "qualitySigma", QUALITY_SIGMA,
                    "postAgeDays", POST_AGE_DAYS));
            report.put("config", config);

            Map<String, Object> dataset = new LinkedHashMap<>();
            dataset.put("posts", posts.size());
            dataset.put("follows", followsCreated);
            dataset.put("visibleLikes", visibleLikes);
            dataset.put("hiddenLikes", hiddenLikes);
            dataset.put("meanCandidatePool", r4(poolSum / cases.size()));
            dataset.put("candidateCoverageOfHidden", r4(coverageSum / cases.size()));
            dataset.put("embedFailures", embedFailures);
            dataset.put("tuningUsers", tuning.size());
            dataset.put("validationUsers", validation.size());
            report.put("dataset", dataset);

            Map<String, Object> bestReport = new LinkedHashMap<>();
            bestReport.put("weights", Arrays.stream(best).boxed().toList());
            bestReport.put("tuningNdcg", r4(tuneNdcg[bestIdx]));
            bestReport.put("validationNdcg", r4(valNdcg[bestIdx]));
            report.put("bestOnTuning", bestReport);
            report.put("methods", methodReports);
            report.put("userGroups", Arrays.stream(boxed(isTuning)).map(t -> t ? "tuning" : "validation").toList());
            report.put("perUserNdcg", perUserNdcg);

            if (p.includeGrid()) {
                List<double[]> compact = new ArrayList<>(grid.size());
                for (int g = 0; g < grid.size(); g++) {
                    double[] w = grid.get(g);
                    compact.add(new double[]{w[0], w[1], w[2], w[3], w[4], r4(tuneNdcg[g]), r4(valNdcg[g])});
                }
                report.put("gridColumns", List.of("popularity", "itemCf", "graphSim", "semantic", "recency", "tuningNdcg", "validationNdcg"));
                report.put("grid", compact);
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
            if (graphTouched) {
                try {
                    removeGraphNodes();
                    neo4jCleaned = true;
                    // повертаємо вектори реальних користувачів у стан, узгоджений із очищеним графом
                    graphEmbeddingService.generateEmbeddings(EMBEDDING_DIM);
                } catch (Exception e) {
                    neo4jNote = String.valueOf(e.getMessage());
                    log.warn("Neo4j cleanup problem: {}", e.getMessage());
                }
            }
        }

        report.put("cleanup", Map.of("usersDeleted", usersDeleted, "failed", deleteFailures,
                "neo4jCleaned", neo4jCleaned, "neo4jNote", neo4jNote));
        report.put("durationSeconds", r4((System.nanoTime() - started) / 1_000_000_000.0));
        return report;
    }

    /** Прибирає користувачів rec_* (Postgres і Neo4j), якщо попередній прогін було перервано. */
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
        String note = "";
        try {
            removeGraphNodes();
        } catch (Exception e) {
            note = String.valueOf(e.getMessage());
        }
        return Map.of("deleted", deleted, "failed", failed, "neo4jNote", note);
    }

    // ===== допоміжне =====

    private void removeGraphNodes() {
        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                tx.run("MATCH (u:User) WHERE u.username STARTS WITH $prefix DETACH DELETE u",
                        Map.of("prefix", PREFIX));
                return null;
            });
        }
    }

    private void validate(Params p) {
        if (p.users() < 12 || p.users() > 200) {
            throw new IllegalArgumentException("users: 12–200 (потрібно щонайменше по два користувачі на тему)");
        }
        if (p.postsPerUser() < 1 || p.postsPerUser() > 10) {
            throw new IllegalArgumentException("postsPerUser: 1–10");
        }
        if (p.likesPerUser() < 5 || p.likesPerUser() > 60) {
            throw new IllegalArgumentException("likesPerUser: 5–60");
        }
        if (p.followsPerUser() < 1 || p.followsPerUser() > 15) {
            throw new IllegalArgumentException("followsPerUser: 1–15");
        }
        if (p.hiddenShare() < 0.1 || p.hiddenShare() > 0.4) {
            throw new IllegalArgumentException("hiddenShare: 0.1–0.4");
        }
        if (p.recencyTauDays() < 0) {
            throw new IllegalArgumentException("recencyTauDays: >= 0 (0 вимикає вплив свіжості на лайки)");
        }
        if (p.k() < 1 || p.k() > 20) {
            throw new IllegalArgumentException("k: 1–20");
        }
        if (p.gridSteps() != 10 && p.gridSteps() != 20) {
            throw new IllegalArgumentException("gridSteps: 10 або 20");
        }
        if (p.candidate() != null) {
            if (p.candidate().length != 5) {
                throw new IllegalArgumentException("candidate: рівно п'ять значень");
            }
            double sum = 0;
            for (double x : p.candidate()) {
                if (x < 0) {
                    throw new IllegalArgumentException("candidate: значення не можуть бути від'ємними");
                }
                sum += x;
            }
            if (Math.abs(sum - 1.0) > 1e-6) {
                throw new IllegalArgumentException("candidate: сума має дорівнювати 1");
            }
        }
    }

    private Map<String, Tag> ensureTags() {
        Map<String, Tag> map = new HashMap<>();
        for (String name : EchoChamberValidationService.TOPICS) {
            Tag tag = tagRepository.findByName(name).orElseGet(() -> {
                Tag t = new Tag();
                t.setName(name);
                return tagRepository.save(t);
            });
            map.put(name, tag);
        }
        return map;
    }

    /** Вибірка без повторень, пропорційна вагам (експоненційні ключі). */
    private List<Integer> sampleDistinct(double[] weights, int count, Random random) {
        int n = weights.length;
        double[] keys = new double[n];
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) {
            idx[i] = i;
            keys[i] = weights[i] > 0
                    ? -Math.log(1.0 - random.nextDouble()) / weights[i]
                    : Double.POSITIVE_INFINITY;
        }
        Arrays.sort(idx, Comparator.comparingDouble(i -> keys[i]));
        List<Integer> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(idx[i]);
        }
        return result;
    }

    private RankingMetrics.Metrics[] evaluateAll(List<WeightSearch.UserCase> cases, Method m, int k) {
        RankingMetrics.Metrics[] out = new RankingMetrics.Metrics[cases.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = WeightSearch.evaluate(cases.get(i), m.weights(), m.bonus(), k);
        }
        return out;
    }

    /** Випадкове ранжування: усереднення за багатьма незалежними перестановками. */
    private RankingMetrics.Metrics[] randomBaseline(List<WeightSearch.UserCase> cases, int k, long seed) {
        Random rnd = new Random(seed);
        RankingMetrics.Metrics[] out = new RankingMetrics.Metrics[cases.size()];
        for (int i = 0; i < out.length; i++) {
            WeightSearch.UserCase uc = cases.get(i);
            List<RankingMetrics.Metrics> draws = new ArrayList<>(RANDOM_DRAWS);
            for (int d = 0; d < RANDOM_DRAWS; d++) {
                double[] tb = new double[uc.size()];
                for (int j = 0; j < tb.length; j++) {
                    tb[j] = rnd.nextDouble();
                }
                draws.add(WeightSearch.evaluate(uc.withTieBreak(tb), new double[5], 0.0, k));
            }
            out[i] = RankingMetrics.mean(draws);
        }
        return out;
    }

    private double[] withoutComponent(double[] weights, int component) {
        double[] w = weights.clone();
        w[component] = 0.0;
        double sum = Arrays.stream(w).sum();
        for (int i = 0; i < w.length; i++) {
            w[i] = w[i] / sum;
        }
        return w;
    }

    private double distance(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) {
            s += (a[i] - b[i]) * (a[i] - b[i]);
        }
        return Math.sqrt(s);
    }

    private Boolean[] boxed(boolean[] values) {
        Boolean[] out = new Boolean[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = values[i];
        }
        return out;
    }

    private Map<String, Object> metricsMap(RankingMetrics.Metrics m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("precision", r4(m.precision()));
        map.put("recall", r4(m.recall()));
        map.put("hitRate", r4(m.hit()));
        map.put("ndcg", r4(m.ndcg()));
        map.put("mrr", r4(m.mrr()));
        return map;
    }

    private static double r4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}