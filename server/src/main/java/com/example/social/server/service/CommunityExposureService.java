package com.example.social.server.service;

import com.example.social.server.entity.Tag;
import com.example.social.server.exception.BadRequestException;
import com.example.social.server.exception.ConflictException;
import com.example.social.server.repository.PostEmbeddingRepository;
import com.example.social.server.service.CommunityExposure.Item;
import com.example.social.server.service.CommunityExposure.ListStats;
import com.example.social.server.service.CommunityStructure.Edge;
import com.example.social.server.service.RecommendationService.CandidateScores;
import com.example.social.server.service.RecommendationService.Weights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Чи замикає стрічка «Для вас» користувачів у їхніх спільнотах (зв'язок рекомендацій з аналізом спільнот).
 *
 * <p>Для вибірки користувачів будується видача з k постів за кількома способами ранжування на тих самих
 * кандидатах: гібридна оцінка з поточними й стандартними вагами, кожна складова окремо, гібридна оцінка
 * з MMR-перестановкою при різних λ і з розбавленням постами інших спільнот ({@link CommunityDiversifier})
 * при різних частках. Для кожної видачі рахуються показники {@link CommunityExposure}: частка постів
 * зі своєї спільноти (порівняно з випадковою), ентропія спільнот, кількість тем, частка постів від підписок,
 * змістова різноманітність (ILD), а також ціна: збережена релевантність (сума гібридних оцінок видачі
 * відносно найкращих k) і збіг з інтересами (частка постів з улюбленими тегами користувача).
 *
 * <p>Власні пости користувача не враховуються: вони не показують, з ким людина стикається у стрічці.
 * Беруться лише ті, хто має вподобання (інакше стрічка — холодний старт із найновіших постів),
 * а якщо в мережі є згенеровані користувачі — лише вони. Дані не змінюються.
 */
@Service
public class CommunityExposureService {

    private static final Logger log = LoggerFactory.getLogger(CommunityExposureService.class);

    /** MMR обирає зі стількох найрелевантніших кандидатів: k × 3 (як у стрічці). */
    private static final int MMR_POOL_FACTOR = 3;
    /** Розв'язує нічиї в ранжуванні за однією складовою на користь свіжіших постів. */
    private static final double TIE_BREAK = 1e-6;
    /** Інтереси користувача — стільки найчастіших тегів серед вподобаних постів. */
    private static final int INTEREST_TAGS = 3;

    private final RecommendationService recommendations;
    private final PostEmbeddingRepository embeddings;
    private final CommunityGraph graph;
    private final CommunityData data;

    public CommunityExposureService(RecommendationService recommendations, PostEmbeddingRepository embeddings,
                                    CommunityGraph graph, CommunityData data) {
        this.recommendations = recommendations;
        this.embeddings = embeddings;
        this.graph = graph;
        this.data = data;
    }

    /**
     * Спосіб ранжування: оцінка кандидата; для MMR — λ; для розбавлення — частка постів інших спільнот.
     */
    private record Ranking(String key, String label, ToDoubleFunction<CandidateScores> score, Double lambda,
                           Double outsideShare) {
        Ranking(String key, String label, ToDoubleFunction<CandidateScores> score, Double lambda) {
            this(key, label, score, lambda, null);
        }
    }

    /** Показники одного способу ранжування, накопичені по користувачах. */
    private static final class Acc {
        final List<Double> own = new ArrayList<>();
        final List<Double> followed = new ArrayList<>();
        final List<Double> communities = new ArrayList<>();
        final List<Double> entropy = new ArrayList<>();
        final List<Double> tags = new ArrayList<>();
        final List<Double> diversity = new ArrayList<>();
        final List<Double> size = new ArrayList<>();
        final List<Double> retained = new ArrayList<>();
        final List<Double> interest = new ArrayList<>();

        void add(ListStats s, double ild, double relevanceRetained, double interestMatch) {
            retained.add(relevanceRetained);
            interest.add(interestMatch);
            own.add(s.ownShare());
            followed.add(s.followedShare());
            communities.add((double) s.communities());
            entropy.add(s.entropy());
            tags.add((double) s.distinctTags());
            diversity.add(ild);
            size.add((double) s.size());
        }
    }

    /**
     * @param users   скільки користувачів узяти у вибірку
     * @param k       довжина видачі
     * @param lambdas λ для MMR (1 — без перестановки)
     * @param outsideShares частки постів інших спільнот для розбавлення (без MMR)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> experiment(boolean leiden, int users, int k, List<Double> lambdas,
                                          List<Double> outsideShares, long seed) {
        if (users < 1 || users > 500) throw new BadRequestException("users: від 1 до 500");
        if (k < 5 || k > 50) throw new BadRequestException("k: від 5 до 50");
        if (lambdas.isEmpty() || lambdas.size() > 9) throw new BadRequestException("lambdas: від 1 до 9 значень");
        if (lambdas.stream().anyMatch(l -> l < 0 || l > 1)) throw new BadRequestException("λ — від 0 до 1");
        if (outsideShares.size() > 9) throw new BadRequestException("outsideShares: до 9 значень");
        if (outsideShares.stream().anyMatch(s -> s <= 0 || s > 1)) {
            throw new BadRequestException("частка інших спільнот — від 0 (не включно) до 1");
        }

        long started = System.nanoTime();
        Map<Long, Long> labels = graph.labels(leiden);
        if (labels.isEmpty()) {
            throw new ConflictException("Спільноти ще не визначені — спершу POST /api/graph/communities");
        }
        Map<Long, Integer> postsByAuthor = data.postsByAuthor();
        Map<Long, Set<Long>> following = new HashMap<>();
        for (Edge e : data.follows()) {
            following.computeIfAbsent(e.from(), x -> new HashSet<>()).add(e.to());
        }
        Map<Long, Integer> size = new HashMap<>();
        labels.values().forEach(l -> size.merge(l, 1, Integer::sum));
        int totalCommunities = (int) size.values().stream().filter(n -> n >= 2).count();

        List<Long> sample = sample(labels, size, users, seed);
        if (sample.isEmpty()) {
            throw new ConflictException("Немає користувачів зі спільнотою й вподобаннями — спершу tools/report/seed.py");
        }

        Map<Long, Map<String, Integer>> likedTags = data.likedTagsByUser();
        Weights current = recommendations.currentWeights();
        List<Ranking> rankings = rankings(current, lambdas, outsideShares);
        Map<String, Acc> results = new LinkedHashMap<>();
        rankings.forEach(r -> results.put(r.key(), new Acc()));
        List<Double> randomShares = new ArrayList<>();
        List<Double> candidateShares = new ArrayList<>();
        Map<Long, List<double[]>> byCommunity = new HashMap<>();   // спільнота → [частка своєї у стрічці, випадкова]

        int skipped = 0;
        for (long userId : sample) {
            List<CandidateScores> candidates = recommendations.computeCandidateScores(userId, null).stream()
                    .filter(c -> c.authorId() != userId)
                    .sorted(Comparator.comparingLong(CandidateScores::postId).reversed())   // однаковий порядок нічиїх
                    .toList();
            if (candidates.size() < k) {
                skipped++;
                continue;
            }
            Long community = labels.get(userId);
            Set<Long> followed = following.getOrDefault(userId, Set.of());
            List<Item> items = candidates.stream().map(c -> new Item(c.authorId(), tagNames(c))).toList();

            Set<String> interests = CommunityExposure.topTags(likedTags.getOrDefault(userId, Map.of()), INTEREST_TAGS);
            double[] hybrid = candidates.stream().mapToDouble(c -> c.total(current)).toArray();
            double bestHybrid = CommunityExposure.topK(hybrid, k).stream().mapToDouble(i -> hybrid[i]).sum();
            List<Integer> outsideOrder = CommunityExposure.topK(hybrid, hybrid.length).stream()
                    .filter(i -> !Objects.equals(community, labels.get(candidates.get(i).authorId())))
                    .toList();

            double random = CommunityExposure.randomOwnShare(userId, labels, postsByAuthor);
            randomShares.add(random);
            candidateShares.add(CommunityExposure.of(items, community, labels, followed, totalCommunities).ownShare());

            for (Ranking r : rankings) {
                double[] relevance = candidates.stream().mapToDouble(r.score()).toArray();
                List<Integer> chosen;
                if (r.outsideShare() != null) {
                    chosen = CommunityDiversifier.interleave(CommunityExposure.topK(relevance, k), outsideOrder,
                            i -> !Objects.equals(community, labels.get(candidates.get(i).authorId())),
                            i -> i, k, r.outsideShare());
                } else if (r.lambda() == null || r.lambda() >= 1.0) {
                    chosen = CommunityExposure.topK(relevance, k);
                } else {
                    chosen = mmr(candidates, relevance, k, r.lambda());
                }
                List<Item> list = chosen.stream().map(items::get).toList();
                ListStats stats = CommunityExposure.of(list, community, labels, followed, totalCommunities);
                double retained = bestHybrid > 0 ? chosen.stream().mapToDouble(i -> hybrid[i]).sum() / bestHybrid : 0;
                results.get(r.key()).add(stats, diversity(chosen.stream().map(i -> candidates.get(i).postId()).toList()),
                        retained, CommunityExposure.interestMatch(list, interests));
                if (r.key().equals("current")) {
                    byCommunity.computeIfAbsent(community, x -> new ArrayList<>())
                            .add(new double[]{stats.ownShare(), random});
                }
            }
        }
        if (randomShares.isEmpty()) {
            throw new ConflictException("У вибраних користувачів замало кандидатів (менше k = " + k + ")");
        }
        double randomMean = CommunityExposure.meanSd(randomShares)[0];

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Ranking r : rankings) {
            rows.add(row(r, results.get(r.key()), randomMean));
        }

        List<Map<String, Object>> communities = new ArrayList<>();
        byCommunity.entrySet().stream()
                .sorted((x, y) -> Integer.compare(y.getValue().size(), x.getValue().size()))
                .forEach(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("community", e.getKey());
                    row.put("size", size.getOrDefault(e.getKey(), 0));
                    row.put("users", e.getValue().size());
                    row.put("ownShare", round(e.getValue().stream().mapToDouble(v -> v[0]).average().orElse(0)));
                    row.put("randomOwnShare", round(e.getValue().stream().mapToDouble(v -> v[1]).average().orElse(0)));
                    communities.add(row);
                });

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("algorithm", leiden ? "leiden" : "louvain");
        config.put("users", users);
        config.put("k", k);
        config.put("lambdas", lambdas);
        config.put("outsideShares", outsideShares);
        config.put("interestTags", INTEREST_TAGS);
        config.put("seed", seed);
        config.put("weights", current);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("config", config);
        result.put("usersEvaluated", randomShares.size());
        result.put("usersSkipped", skipped);
        result.put("communities", totalCommunities);
        result.put("randomOwnShare", round(randomMean));
        result.put("candidateOwnShare", round(CommunityExposure.meanSd(candidateShares)[0]));
        result.put("rankings", rows);
        result.put("byCommunity", communities);
        result.put("durationSeconds", round((System.nanoTime() - started) / 1e9));
        log.info("Спільноти у стрічці: {} користувачів, випадкова частка своєї спільноти {}, за {} с",
                randomShares.size(), round(randomMean), result.get("durationSeconds"));
        return result;
    }

    /** Поточні ваги (з MMR при кожному λ і з розбавленням), стандартні ваги і кожна складова окремо. */
    private static List<Ranking> rankings(Weights current, List<Double> lambdas, List<Double> outsideShares) {
        List<Ranking> list = new ArrayList<>();
        for (double lambda : lambdas) {
            list.add(lambda >= 1.0
                    ? new Ranking("current", "Поточні ваги", c -> c.total(current), null)
                    : new Ranking("current_mmr_" + lambda, "Поточні ваги + MMR λ=" + lambda, c -> c.total(current), lambda));
        }
        if (lambdas.stream().noneMatch(l -> l >= 1.0)) {
            list.add(0, new Ranking("current", "Поточні ваги", c -> c.total(current), null));
        }
        for (double share : outsideShares) {
            list.add(new Ranking("current_outside_" + share, "Поточні ваги + інші спільноти " + share,
                    c -> c.total(current), null, share));
        }
        list.add(new Ranking("default", "Стандартні ваги", c -> c.total(Weights.DEFAULT), null));
        list.add(single("popularity", "Лише популярність", CandidateScores::popularity));
        list.add(single("itemCf", "Лише item-CF", CandidateScores::itemCf));
        list.add(single("graphSim", "Лише граф (node2vec)", CandidateScores::graphSim));
        list.add(single("semantic", "Лише семантика", CandidateScores::semantic));
        list.add(new Ranking("recency", "Лише свіжість", CandidateScores::recency, null));
        list.add(single("followed", "Лише підписки", c -> c.followed() ? 1.0 : 0.0));
        return list;
    }

    private static Ranking single(String key, String label, ToDoubleFunction<CandidateScores> component) {
        return new Ranking(key, label, c -> component.applyAsDouble(c) + TIE_BREAK * c.recency(), null);
    }

    /** MMR на k × 3 найрелевантніших, як у стрічці; повертає індекси в списку кандидатів. */
    private List<Integer> mmr(List<CandidateScores> candidates, double[] relevance, int k, double lambda) {
        List<Integer> pool = CommunityExposure.topK(relevance, k * MMR_POOL_FACTOR);
        double max = pool.stream().mapToDouble(i -> relevance[i]).max().orElse(0);
        double[] normalized = pool.stream().mapToDouble(i -> max > 0 ? Math.max(relevance[i], 0) / max : 0).toArray();
        double[][] similarity = embeddings.findPairwiseSimilarity(
                pool.stream().map(i -> candidates.get(i).postId()).toList());
        return MmrReranker.rerank(normalized, similarity, k, lambda).stream().map(pool::get).toList();
    }

    /** Змістова різноманітність видачі (ILD) за векторами постів. */
    private double diversity(List<Long> postIds) {
        double[][] similarity = embeddings.findPairwiseSimilarity(postIds);
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < postIds.size(); i++) {
            all.add(i);
        }
        return MmrReranker.intraListDiversity(all, similarity);
    }

    /**
     * Вибірка: користувачі зі спільнотою (не поодинокі) і вподобаннями; якщо є згенеровані — лише вони.
     * Порядок фіксується зерном, тож повтор дає тих самих людей.
     */
    private List<Long> sample(Map<Long, Long> labels, Map<Long, Integer> size, int users, long seed) {
        Set<Long> likers = data.likers();
        Set<Long> seeded = data.groundTruth().keySet();
        List<Long> pool = labels.keySet().stream()
                .filter(u -> size.get(labels.get(u)) >= 2 && likers.contains(u))
                .filter(u -> seeded.isEmpty() || seeded.contains(u))
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Collections.shuffle(pool, new Random(seed));
        return pool.subList(0, Math.min(users, pool.size()));
    }

    private static Map<String, Object> row(Ranking r, Acc a, double randomMean) {
        double[] own = CommunityExposure.meanSd(a.own);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", r.key());
        row.put("label", r.label());
        row.put("lambda", r.lambda() == null ? 1.0 : r.lambda());
        row.put("outsideShare", r.outsideShare() == null ? 0.0 : r.outsideShare());
        row.put("ownShare", round(own[0]));
        row.put("ownShareSd", round(own[1]));
        row.put("lift", randomMean > 0 ? round(own[0] / randomMean) : null);
        row.put("followedShare", round(CommunityExposure.meanSd(a.followed)[0]));
        row.put("communitiesInList", round(CommunityExposure.meanSd(a.communities)[0]));
        row.put("entropy", round(CommunityExposure.meanSd(a.entropy)[0]));
        row.put("distinctTags", round(CommunityExposure.meanSd(a.tags)[0]));
        row.put("diversity", round(CommunityExposure.meanSd(a.diversity)[0]));
        row.put("listSize", round(CommunityExposure.meanSd(a.size)[0]));
        row.put("relevanceRetained", round(CommunityExposure.meanSd(a.retained)[0]));
        row.put("interestMatch", round(CommunityExposure.meanSd(a.interest)[0]));
        return row;
    }

    private static List<String> tagNames(CandidateScores c) {
        return c.post().getTags().stream().map(Tag::getName).toList();
    }

    private static double round(double v) {
        return Math.round(v * 10000) / 10000.0;
    }
}