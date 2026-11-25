package com.example.social.server.service;

import com.example.social.server.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Генератор реалістичної мережі для демонстрації та звіту.
 *
 * <p>Модель: користувачі належать до спільнот з інтересами (темами). Частина спільнот «вузькі» —
 * одна тема й однаковий емоційний тон (потенційні ехо-камери), решта — різнопланові.
 * Підписки гомофільні (переважно всередині спільноти) з преференційним приєднанням до популярних;
 * «мости» — користувачі з інтересами двох спільнот. Пости — узгоджені сюжети (заголовок + текст) з банку
 * {@code seed/content.json}; настрій сюжету відповідає настрою автора, теги — темі; дати розподілені
 * за останні N днів (частіше — ближче до сьогодні). Вподобання — за інтересами та від підписок,
 * з перевагою популярних постів; коментарі погоджуються або сперечаються залежно від тону, автори відповідають.
 *
 * <p>Усі згенеровані акаунти мають email {@value #EMAIL_DOMAIN} — так їх можна видалити одним запитом.
 *
 * <p>Стан мережі ({@link Network}: користувачі з інтересами, тоном і активністю, пости, підписки)
 * доступний у пакеті — його продовжує {@link SeedEvolutionService}, симулюючи наступні тижні.
 */
@Service
public class SeedGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(SeedGeneratorService.class);

    public static final String EMAIL_DOMAIN = "@seed.nexus.local";
    public static final String SEED_PASSWORD = "password123";
    /** Тимчасові мережі експериментів (якість виявлення спільнот) — окремо від основної згенерованої мережі. */
    public static final String EXPERIMENT_DOMAIN = "@experiment.nexus.local";

    /** Параметри генерації (значення за замовчуванням — у контролері). */
    public record Params(int users, int communities, int days, double avgPosts, double avgFollows,
                         double avgLikes, double homophily, double narrowShare, double bridgeShare,
                         double commentRate, double replyRate, double saveRate, long seed) {

        void validate() {
            if (users < 6 || users > 2000) throw new BadRequestException("users: від 6 до 2000");
            if (communities < 2 || communities > users / 3) throw new BadRequestException("communities: від 2 до users/3");
            if (days < 1 || days > 365) throw new BadRequestException("days: від 1 до 365");
            for (double share : new double[]{homophily, narrowShare, bridgeShare, commentRate, replyRate, saveRate}) {
                if (share < 0 || share > 1) throw new BadRequestException("частки мають бути в межах [0, 1]");
            }
            if (avgPosts <= 0 || avgFollows <= 0 || avgLikes < 0) throw new BadRequestException("середні значення мають бути додатні");
        }
    }

    // ===== Банк текстів =====

    /** Один сюжет поста: заголовок і текст, що відповідають одне одному, та їхній настрій. */
    public record Item(String title, String text, String mood) {}

    public record Topic(String key, List<String> tags, List<Item> items) {}

    public record Content(List<Topic> topics,
                          Map<String, List<String>> openers, Map<String, List<String>> closers,
                          List<String> addons,
                          Map<String, List<String>> comments, List<String> replies,
                          List<String> firstNamesMale, List<String> firstNamesFemale,
                          List<String> lastNames, List<String> about) {}

    // ===== Модель =====

    record Community(int index, Map<Integer, Double> interests, boolean narrow, double bias, double biasSpread) {}

    static final class SeedUser {
        long id;
        String username;
        int community;
        boolean bridge;
        Map<Integer, Double> interests;
        double activity;
        double bias;
        LocalDateTime createdAt;
        final Set<Long> following = new LinkedHashSet<>();
        /** Пости, які користувач уже вподобав, — щоб не вподобати двічі (і в наступних тижнях еволюції). */
        final Set<Long> liked = new HashSet<>();
    }

    record SeedPost(long id, long authorId, int topic, int sentiment, LocalDateTime at) {}

    /** Стан згенерованої мережі в пам'яті — для продовження симуляції ({@link SeedEvolutionService}). */
    static final class Network {
        final Params params;
        final Random rnd;
        String emailDomain = EMAIL_DOMAIN;
        final long started = System.nanoTime();
        List<Community> communities;
        final List<SeedUser> users = new ArrayList<>();
        final List<SeedPost> posts = new ArrayList<>();
        Map<String, Long> tagIds;
        /** Кількість читачів — для преференційного приєднання. */
        final Map<Long, Integer> indegree = new HashMap<>();
        final Map<Long, Integer> likesPerPost = new HashMap<>();
        Set<String> usedNames;
        String passwordHash;
        int follows;
        Map<String, Integer> interactions;

        Network(Params params, Random rnd) {
            this.params = params;
            this.rnd = rnd;
        }

        SeedUser user(long id) {
            return users.stream().filter(u -> u.id == id).findFirst().orElseThrow();
        }
    }

    /** Накопичувач вподобань, збережень і коментарів — вставляються пакетом наприкінці. */
    static final class InteractionBatch {
        final List<Object[]> likes = new ArrayList<>();
        final List<Object[]> saves = new ArrayList<>();
        int comments;
        int replies;
    }

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final Content content;

    public SeedGeneratorService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        try (InputStream in = getClass().getResourceAsStream("/seed/content.json")) {
            if (in == null) {
                throw new IllegalStateException("Немає ресурсу seed/content.json");
            }
            this.content = objectMapper.readValue(in, Content.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Не вдалося прочитати seed/content.json", e);
        }
    }

    // ===== Генерація =====

    @Transactional
    public Map<String, Object> generate(Params p) {
        return summarize(buildNetwork(p, LocalDateTime.now()));
    }

    /**
     * Згенерувати мережу, у якій «зараз» — момент {@code now} (дати постів — за {@code days} днів до нього).
     * Без власної транзакції: викликається з {@link #generate} або з транзакції еволюції.
     */
    Network buildNetwork(Params p, LocalDateTime now) {
        return buildNetwork(p, now, EMAIL_DOMAIN);
    }

    /** Те саме з іншим доменом пошти — для тимчасових мереж, які прибирає {@link #cleanup(String)}. */
    Network buildNetwork(Params p, LocalDateTime now, String emailDomain) {
        p.validate();
        Network net = new Network(p, new Random(p.seed()));
        net.emailDomain = emailDomain;

        net.communities = buildCommunities(p, net.rnd);
        createUsers(net, now);
        net.tagIds = ensureTags();
        createPosts(net, now);
        net.follows = createFollows(net, now);
        net.interactions = createInteractions(net, now);
        return net;
    }

    Map<String, Object> summarize(Network net) {
        long sameCommunity = 0;
        long totalFollows = 0;
        Map<Long, SeedUser> byId = new HashMap<>();
        net.users.forEach(u -> byId.put(u.id, u));
        for (SeedUser u : net.users) {
            for (long target : u.following) {
                totalFollows++;
                if (byId.get(target).community == u.community) {
                    sameCommunity++;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("usersCreated", net.users.size());
        result.put("postsCreated", net.posts.size());
        result.put("followsCreated", net.follows);
        result.putAll(net.interactions);
        result.put("followsWithinCommunityShare", totalFollows == 0 ? 0 : round(sameCommunity / (double) totalFollows));
        result.put("communities", net.communities.stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", c.index());
            row.put("type", c.narrow() ? "narrow" : "diverse");
            row.put("topics", c.interests().keySet().stream().map(t -> content.topics().get(t).key()).toList());
            row.put("tone", round(c.bias()));
            row.put("members", net.users.stream().filter(u -> u.community == c.index()).count());
            return row;
        }).toList());
        result.put("password", SEED_PASSWORD);
        result.put("emailDomain", EMAIL_DOMAIN);
        result.put("durationSeconds", round((System.nanoTime() - net.started) / 1e9));
        result.put("next", List.of(
                "POST /api/dev/backfill-embeddings — векторні представлення постів (семантика)",
                "POST /api/dev/backfill-sentiment — тональність постів",
                "POST /api/graph/embeddings — node2vec для «Схожих інтересів»",
                "POST /api/admin/echo-history — знімок ехо-камер"));
        log.info("Seed generated: {}", result);
        return result;
    }

    Content content() {
        return content;
    }

    /** Видалити всі згенеровані акаунти (каскадом — їхні пости, підписки, лайки, коментарі). */
    @Transactional
    public Map<String, Object> cleanup() {
        return cleanup(EMAIL_DOMAIN);
    }

    /** Видалити акаунти з доменом пошти {@code emailDomain} (каскадом — усе їхнє). */
    Map<String, Object> cleanup(String emailDomain) {
        int deleted = jdbc.update("DELETE FROM \"user\" WHERE user_id IN "
                + "(SELECT user_id FROM user_private WHERE email LIKE ?)", "%" + emailDomain);
        log.info("Seed cleanup ({}): {} users deleted", emailDomain, deleted);
        return Map.of("usersDeleted", deleted);
    }

    // ===== Спільноти й користувачі =====

    private List<Community> buildCommunities(Params p, Random rnd) {
        int topicCount = content.topics().size();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < topicCount; i++) {
            order.add(i);
        }
        Collections.shuffle(order, rnd);

        int narrowCount = (int) Math.round(p.communities() * p.narrowShare());
        List<Community> result = new ArrayList<>();
        for (int c = 0; c < p.communities(); c++) {
            boolean narrow = c < narrowCount;
            Map<Integer, Double> interests = new LinkedHashMap<>();
            int main = order.get(c % topicCount);
            if (narrow) {
                interests.put(main, 1.0);
                // вузька спільнота: однаковий виразний тон — то захоплений, то критичний
                double bias = c % 2 == 0 ? 0.6 : -0.5;
                result.add(new Community(c, interests, true, bias, 0.15));
            } else {
                interests.put(main, 0.5);
                interests.merge(order.get((c + 3) % topicCount), 0.3, Double::sum);
                interests.merge(order.get((c + 6) % topicCount), 0.2, Double::sum);
                result.add(new Community(c, interests, false, 0.1, 0.45));
            }
        }
        return result;
    }

    private void createUsers(Network net, LocalDateTime now) {
        Params p = net.params;
        Random rnd = net.rnd;
        net.passwordHash = passwordEncoder.encode(SEED_PASSWORD);   // один хеш на всіх — bcrypt повільний
        net.usedNames = new HashSet<>(jdbc.queryForList("SELECT username FROM \"user\"", String.class));

        List<Integer> assignment = new ArrayList<>();
        for (int i = 0; i < p.users(); i++) {
            assignment.add(i % net.communities.size());
        }
        Collections.shuffle(assignment, rnd);

        for (int i = 0; i < p.users(); i++) {
            Community c = net.communities.get(assignment.get(i));
            net.users.add(insertUser(net, c, r -> now.minusDays(p.days()).minusHours(r.nextInt(24 * 60))));
        }
    }

    /**
     * Новий користувач спільноти {@code c}: інтереси спільноти (у «мостів» — ще й сусідньої), тон, активність,
     * ім'я з банку. {@code createdAt} отримує той самий генератор випадковостей у потрібний момент послідовності.
     */
    SeedUser insertUser(Network net, Community c, Function<Random, LocalDateTime> createdAt) {
        Params p = net.params;
        Random rnd = net.rnd;
        List<Community> communities = net.communities;
        SeedUser u = new SeedUser();
        u.community = c.index();
        u.interests = new HashMap<>(c.interests());
        if (rnd.nextDouble() < p.bridgeShare()) {
            // «міст»: інтереси ще однієї спільноти
            Community other = communities.get((c.index() + 1 + rnd.nextInt(communities.size() - 1)) % communities.size());
            other.interests().forEach((t, w) -> u.interests.merge(t, w * 0.8, Double::sum));
            u.bridge = true;
        }
        if (rnd.nextDouble() < 0.3) {
            u.interests.merge(rnd.nextInt(content.topics().size()), 0.15, Double::sum);
        }
        normalize(u.interests);
        u.activity = Math.exp(rnd.nextGaussian() * 0.7) / 1.28;   // log-normal із середнім ≈ 1
        u.bias = clamp(c.bias() + rnd.nextGaussian() * c.biasSpread(), -1, 1);
        u.createdAt = createdAt.apply(rnd);

        boolean female = rnd.nextBoolean();
        String first = pick(female ? content.firstNamesFemale() : content.firstNamesMale(), rnd);
        String last = pick(content.lastNames(), rnd);
        String username = uniqueUsername(first, last, net.usedNames, rnd);
        String about = pick(content.about(), rnd).replace("{topics}", topicList(u.interests));

        u.username = username;
        u.id = jdbc.queryForObject(
                "INSERT INTO \"user\"(uid, username, display_name, about_me, created_at) "
                        + "VALUES (?, ?, ?, ?, ?) RETURNING user_id",
                Long.class, UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                username, first + " " + last, about, Timestamp.valueOf(u.createdAt));
        jdbc.update("INSERT INTO user_private(user_id, email, password_hash) VALUES (?, ?, ?)",
                u.id, username + net.emailDomain, net.passwordHash);
        // правильна відповідь для оцінки виявлення спільнот
        jdbc.update("INSERT INTO seed_ground_truth(user_id, community, narrow) VALUES (?, ?, ?)",
                u.id, c.index(), c.narrow());
        return u;
    }

    // ===== Пости =====

    private Map<String, Long> ensureTags() {
        Map<String, Long> ids = new HashMap<>();
        for (Topic topic : content.topics()) {
            for (String tag : topic.tags()) {
                jdbc.update("INSERT INTO tags(name) VALUES (?) ON CONFLICT (name) DO NOTHING", tag);
                ids.put(tag, jdbc.queryForObject("SELECT tag_id FROM tags WHERE name = ?", Long.class, tag));
            }
        }
        return ids;
    }

    private void createPosts(Network net, LocalDateTime now) {
        Params p = net.params;
        List<Object[]> postTags = new ArrayList<>();
        for (SeedUser u : net.users) {
            int count = Math.min(40, poisson(p.avgPosts() * u.activity, net.rnd));
            for (int i = 0; i < count; i++) {
                // свіжіші пости трапляються частіше (степінь > 1 зсуває до «сьогодні»)
                insertPost(net, u, r -> now.minusMinutes((long) (Math.pow(r.nextDouble(), 1.6) * p.days() * 24 * 60)),
                        postTags);
            }
        }
        insertPostTags(postTags);
    }

    /**
     * Пост користувача на тему з його поточних інтересів, з настроєм за його тоном.
     * Теги додаються до {@code postTags} — їх вставляє {@link #insertPostTags} пакетом.
     */
    SeedPost insertPost(Network net, SeedUser u, Function<Random, LocalDateTime> at, List<Object[]> postTags) {
        Random rnd = net.rnd;
        int topicIndex = sampleWeighted(u.interests, rnd);
        Topic topic = content.topics().get(topicIndex);
        double tone = u.bias + rnd.nextGaussian() * 0.35;
        String wanted = tone > 0.25 ? "positive" : tone < -0.25 ? "negative" : "neutral";
        Item item = pickItem(topic, wanted, rnd);
        String mood = item.mood();
        int sentiment = "positive".equals(mood) ? 1 : "negative".equals(mood) ? -1 : 0;

        StringBuilder text = new StringBuilder();
        text.append(pick(content.openers().get(mood), rnd)).append(' ').append(item.text());
        if (rnd.nextDouble() < 0.35) {
            text.append(' ').append(pick(content.addons(), rnd));
        }
        text.append(' ').append(pick(content.closers().get(mood), rnd));

        LocalDateTime when = at.apply(rnd);

        long postId = jdbc.queryForObject(
                "INSERT INTO posts(user_id, label, text, created_date) VALUES (?, ?, ?, ?) RETURNING post_id",
                Long.class, u.id, item.title(), text.toString(), Timestamp.valueOf(when));

        Set<String> tags = new LinkedHashSet<>();
        tags.add(topic.key());
        for (String tag : topic.tags()) {
            if (!tag.equals(topic.key()) && rnd.nextDouble() < 0.45) {
                tags.add(tag);
            }
        }
        if (rnd.nextDouble() < 0.05) {   // зрідка — тег «не в тему»
            tags.add(pick(content.topics().get(rnd.nextInt(content.topics().size())).tags(), rnd));
        }
        tags.forEach(tag -> postTags.add(new Object[]{postId, net.tagIds.get(tag)}));
        SeedPost post = new SeedPost(postId, u.id, topicIndex, sentiment, when);
        net.posts.add(post);
        return post;
    }

    void saveRole(long userId, String role, Integer targetCommunity) {
        jdbc.update("UPDATE seed_ground_truth SET role = ?, target_community = ? WHERE user_id = ?",
                role, targetCommunity, userId);
    }

    void insertPostTags(List<Object[]> postTags) {
        jdbc.batchUpdate("INSERT INTO post_tags(post_id, tag_id) VALUES (?, ?) ON CONFLICT DO NOTHING", postTags);
    }

    /** Сюжет потрібного настрою; якщо такого в темі немає — будь-який. */
    static Item pickItem(Topic topic, String mood, Random rnd) {
        List<Item> matching = topic.items().stream().filter(i -> mood.equals(i.mood())).toList();
        return pick(matching.isEmpty() ? topic.items() : matching, rnd);
    }

    // ===== Підписки =====

    private int createFollows(Network net, LocalDateTime now) {
        Params p = net.params;
        Random rnd = net.rnd;
        List<SeedUser> users = net.users;
        Map<Integer, List<SeedUser>> byCommunity = new HashMap<>();
        users.forEach(u -> byCommunity.computeIfAbsent(u.community, k -> new ArrayList<>()).add(u));
        Map<Long, Integer> indegree = net.indegree;
        List<Object[]> rows = new ArrayList<>();

        List<SeedUser> order = new ArrayList<>(users);
        Collections.shuffle(order, rnd);
        for (SeedUser u : order) {
            int target = (int) Math.round(p.avgFollows() * (0.4 + 0.6 * u.activity) * (0.7 + 0.6 * rnd.nextDouble()));
            target = Math.max(1, Math.min(users.size() - 1, target));
            List<SeedUser> own = byCommunity.get(u.community);
            double homophily = u.bridge ? p.homophily() * 0.6 : p.homophily();

            for (int attempt = 0; attempt < target * 6 && u.following.size() < target; attempt++) {
                List<SeedUser> pool = rnd.nextDouble() < homophily && own.size() > 1 ? own : users;
                SeedUser to = preferential(pool, indegree, rnd);
                if (to.id == u.id || !u.following.add(to.id)) {
                    continue;
                }
                indegree.merge(to.id, 1, Integer::sum);
                LocalDateTime from = u.createdAt.isAfter(to.createdAt) ? u.createdAt : to.createdAt;
                rows.add(new Object[]{u.id, to.id, Timestamp.valueOf(between(from, now, rnd))});
            }
        }
        jdbc.batchUpdate("INSERT INTO followers(follower_id, following_id, created_at) VALUES (?, ?, ?)", rows);
        return rows.size();
    }

    /** Підписка u → to з датою at; false — вже підписаний або це він сам. */
    boolean follow(Network net, SeedUser u, SeedUser to, LocalDateTime at) {
        if (to.id == u.id || !u.following.add(to.id)) {
            return false;
        }
        net.indegree.merge(to.id, 1, Integer::sum);
        jdbc.update("INSERT INTO followers(follower_id, following_id, created_at) VALUES (?, ?, ?)",
                u.id, to.id, Timestamp.valueOf(at));
        return true;
    }

    /** Відписка u → to; false — не був підписаний. */
    boolean unfollow(Network net, SeedUser u, SeedUser to) {
        if (!u.following.remove(to.id)) {
            return false;
        }
        net.indegree.merge(to.id, -1, Integer::sum);
        jdbc.update("DELETE FROM followers WHERE follower_id = ? AND following_id = ?", u.id, to.id);
        return true;
    }

    /** Популярніших обирають частіше: вага = кількість читачів + 1. */
    static SeedUser preferential(List<SeedUser> pool, Map<Long, Integer> indegree, Random rnd) {
        double total = 0;
        for (SeedUser u : pool) {
            total += indegree.getOrDefault(u.id, 0) + 1;
        }
        double r = rnd.nextDouble() * total;
        for (SeedUser u : pool) {
            r -= indegree.getOrDefault(u.id, 0) + 1;
            if (r <= 0) {
                return u;
            }
        }
        return pool.get(pool.size() - 1);
    }

    // ===== Вподобання, коментарі, збереження =====

    private Map<String, Integer> createInteractions(Network net, LocalDateTime now) {
        Params p = net.params;
        Map<Integer, List<SeedPost>> byTopic = new HashMap<>();
        Map<Long, List<SeedPost>> byAuthor = new HashMap<>();
        groupPosts(net.posts, byTopic, byAuthor);

        InteractionBatch batch = new InteractionBatch();
        for (SeedUser u : net.users) {
            int target = Math.min(200, poisson(p.avgLikes() * u.activity, net.rnd));
            interact(net, u, target, byTopic, byAuthor, now, batch);
        }
        return flushInteractions(batch);
    }

    static void groupPosts(List<SeedPost> posts, Map<Integer, List<SeedPost>> byTopic, Map<Long, List<SeedPost>> byAuthor) {
        for (SeedPost post : posts) {
            byTopic.computeIfAbsent(post.topic(), k -> new ArrayList<>()).add(post);
            byAuthor.computeIfAbsent(post.authorId(), k -> new ArrayList<>()).add(post);
        }
    }

    /**
     * До {@code target} вподобань користувача серед постів {@code byTopic}/{@code byAuthor}: за інтересами
     * і від підписок, з перевагою популярних; частина — із збереженням, коментарем і відповіддю автора.
     */
    void interact(Network net, SeedUser u, int target, Map<Integer, List<SeedPost>> byTopic,
                  Map<Long, List<SeedPost>> byAuthor, LocalDateTime now, InteractionBatch batch) {
        Params p = net.params;
        Random rnd = net.rnd;
        Map<Long, Integer> likesPerPost = net.likesPerPost;
        List<Long> followed = new ArrayList<>(u.following);
        double maxInterest = Collections.max(u.interests.values());
        int likedNow = 0;

        for (int attempt = 0; attempt < target * 6 && likedNow < target; attempt++) {
            List<SeedPost> source;
            boolean viaFollow = !followed.isEmpty() && rnd.nextDouble() < 0.45;
            if (viaFollow) {
                source = byAuthor.get(followed.get(rnd.nextInt(followed.size())));
            } else {
                int topic = rnd.nextDouble() < 0.85 ? sampleWeighted(u.interests, rnd)
                        : rnd.nextInt(content.topics().size());
                source = byTopic.get(topic);
            }
            if (source == null || source.isEmpty()) {
                continue;
            }
            // «турнір» із двох: популярніший пост має більше шансів зібрати ще вподобання
            SeedPost a = source.get(rnd.nextInt(source.size()));
            SeedPost b = source.get(rnd.nextInt(source.size()));
            SeedPost post = likesPerPost.getOrDefault(b.id(), 0) > likesPerPost.getOrDefault(a.id(), 0) ? b : a;
            if (post.authorId() == u.id || u.liked.contains(post.id())) {
                continue;
            }
            // пост підписки не в тему подобається рідше
            double interest = u.interests.getOrDefault(post.topic(), 0.0) / maxInterest;
            if (viaFollow && rnd.nextDouble() > 0.35 + 0.65 * interest) {
                continue;
            }

            u.liked.add(post.id());
            likedNow++;
            likesPerPost.merge(post.id(), 1, Integer::sum);
            LocalDateTime likedAt = within(post.at(), Duration.ofDays(4), now, rnd);
            batch.likes.add(new Object[]{u.id, post.id(), Timestamp.valueOf(likedAt)});

            if (rnd.nextDouble() < p.saveRate()) {
                batch.saves.add(new Object[]{u.id, post.id(), Timestamp.valueOf(likedAt)});
            }
            if (rnd.nextDouble() < p.commentRate()) {
                LocalDateTime commentedAt = within(likedAt, Duration.ofHours(6), now, rnd);
                long commentId = jdbc.queryForObject(
                        "INSERT INTO comments(post_id, user_id, text, created_at) VALUES (?, ?, ?, ?) RETURNING comment_id",
                        Long.class, post.id(), u.id, commentText(u, post, rnd), Timestamp.valueOf(commentedAt));
                batch.comments++;
                if (rnd.nextDouble() < p.replyRate()) {
                    jdbc.update("INSERT INTO comments(post_id, user_id, text, parent_comment_id, created_at) "
                                    + "VALUES (?, ?, ?, ?, ?)",
                            post.id(), post.authorId(), pick(content.replies(), rnd), commentId,
                            Timestamp.valueOf(within(commentedAt, Duration.ofHours(12), now, rnd)));
                    batch.replies++;
                }
            }
        }
    }

    Map<String, Integer> flushInteractions(InteractionBatch batch) {
        jdbc.batchUpdate("INSERT INTO post_likes(user_id, post_id, liked_at) VALUES (?, ?, ?)", batch.likes);
        jdbc.batchUpdate("INSERT INTO saved_posts(user_id, post_id, saved_at) VALUES (?, ?, ?)", batch.saves);

        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("likesCreated", batch.likes.size());
        result.put("commentsCreated", batch.comments);
        result.put("repliesCreated", batch.replies);
        result.put("savesCreated", batch.saves.size());
        return result;
    }

    /** Згода, коли настрій коментатора збігається з тоном поста; інакше — суперечка; іноді питання. */
    private String commentText(SeedUser u, SeedPost post, Random rnd) {
        if (rnd.nextDouble() < 0.25) {
            return pick(content.comments().get("question"), rnd);
        }
        int userMood = u.bias > 0.25 ? 1 : u.bias < -0.25 ? -1 : 0;
        boolean agrees = userMood == post.sentiment() || (post.sentiment() == 0 && userMood == 0);
        if (rnd.nextDouble() < 0.2) {
            agrees = !agrees;   // трохи шуму: люди не завжди передбачувані
        }
        return pick(content.comments().get(agrees ? "agree" : "disagree"), rnd);
    }

    // ===== Допоміжне =====

    private String topicList(Map<Integer, Double> interests) {
        return interests.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(2)
                .map(e -> content.topics().get(e.getKey()).key())
                .reduce((a, b) -> a + " і " + b)
                .orElse("усе потроху");
    }

    private static String uniqueUsername(String first, String last, Set<String> used, Random rnd) {
        String base = translit(first) + "." + translit(last);
        for (int attempt = 0; attempt < 50; attempt++) {
            String candidate = base + (10 + rnd.nextInt(990));
            if (used.add(candidate)) {
                return candidate;
            }
        }
        String fallback = base + "." + UUID.randomUUID().toString().substring(0, 6);
        used.add(fallback);
        return fallback;
    }

    private static final Map<Character, String> TRANSLIT = new HashMap<>();

    static {
        String[][] pairs = {{"а", "a"}, {"б", "b"}, {"в", "v"}, {"г", "h"}, {"ґ", "g"}, {"д", "d"}, {"е", "e"},
                {"є", "ie"}, {"ж", "zh"}, {"з", "z"}, {"и", "y"}, {"і", "i"}, {"ї", "i"}, {"й", "i"},
                {"к", "k"}, {"л", "l"}, {"м", "m"}, {"н", "n"}, {"о", "o"}, {"п", "p"}, {"р", "r"},
                {"с", "s"}, {"т", "t"}, {"у", "u"}, {"ф", "f"}, {"х", "kh"}, {"ц", "ts"}, {"ч", "ch"},
                {"ш", "sh"}, {"щ", "shch"}, {"ь", ""}, {"ю", "iu"}, {"я", "ia"}, {"'", ""}, {"’", ""}};
        for (String[] pair : pairs) {
            TRANSLIT.put(pair[0].charAt(0), pair[1]);
        }
    }

    static String translit(String value) {
        StringBuilder sb = new StringBuilder();
        for (char ch : value.toLowerCase().toCharArray()) {
            sb.append(TRANSLIT.getOrDefault(ch, Character.isLetterOrDigit(ch) ? String.valueOf(ch) : ""));
        }
        return sb.toString();
    }

    static <T> T pick(List<T> list, Random rnd) {
        return list.get(rnd.nextInt(list.size()));
    }

    static int sampleWeighted(Map<Integer, Double> weights, Random rnd) {
        double r = rnd.nextDouble() * weights.values().stream().mapToDouble(Double::doubleValue).sum();
        int last = -1;
        for (Map.Entry<Integer, Double> e : weights.entrySet()) {
            last = e.getKey();
            r -= e.getValue();
            if (r <= 0) {
                return last;
            }
        }
        return last;
    }

    static void normalize(Map<Integer, Double> weights) {
        double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        weights.replaceAll((k, v) -> v / sum);
    }

    /** Пуассон: для малих λ — точний алгоритм Кнута, для великих — нормальне наближення. */
    static int poisson(double lambda, Random rnd) {
        if (lambda <= 0) {
            return 0;
        }
        if (lambda > 30) {
            return Math.max(0, (int) Math.round(lambda + Math.sqrt(lambda) * rnd.nextGaussian()));
        }
        double limit = Math.exp(-lambda);
        double product = rnd.nextDouble();
        int k = 0;
        while (product > limit) {
            product *= rnd.nextDouble();
            k++;
        }
        return k;
    }

    static LocalDateTime between(LocalDateTime from, LocalDateTime to, Random rnd) {
        long minutes = Math.max(1, Duration.between(from, to).toMinutes());
        return from.plusMinutes((long) (rnd.nextDouble() * minutes));
    }

    /** Момент після start, але не пізніше ніж через max і не в майбутньому. */
    static LocalDateTime within(LocalDateTime start, Duration max, LocalDateTime now, Random rnd) {
        LocalDateTime end = start.plus(max).isBefore(now) ? start.plus(max) : now;
        return end.isAfter(start) ? between(start, end, rnd) : start;
    }

    static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}