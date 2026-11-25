package com.example.social.server.service;

import com.example.social.server.exception.BadRequestException;
import com.example.social.server.service.SeedGeneratorService.Community;
import com.example.social.server.service.SeedGeneratorService.InteractionBatch;
import com.example.social.server.service.SeedGeneratorService.Network;
import com.example.social.server.service.SeedGeneratorService.SeedPost;
import com.example.social.server.service.SeedGeneratorService.SeedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Еволюція згенерованої мережі в часі — для траєкторій користувачів та історії ехо-камер.
 *
 * <p>Спершу {@link SeedGeneratorService} будує базову мережу (її «сьогодні» зсунуто на {@code epochs} тижнів
 * у минуле), далі мережа живе ще {@code epochs} тижнів. Щотижня користувачі публікують і вподобують
 * свіжі пости, а частина з них має <b>роль із відомою наперед поведінкою</b>:
 * <ul>
 *   <li><b>затягування</b> — учасник різнопланової спільноти поступово звужує інтереси й тон до однієї
 *       вузької спільноти, підписується на її учасників і відписується від колишніх;</li>
 *   <li><b>вихід</b> — учасник вузької спільноти починає цікавитися різними темами, підписується на людей
 *       з інших спільнот і відписується від своєї;</li>
 *   <li><b>міграція</b> — користувач переходить з однієї спільноти в іншу (інтереси, тон, підписки);</li>
 *   <li><b>новачки</b> реєструються посеред симуляції, <b>ті, хто йде,</b> поступово згасають
 *       і втрачають зв'язки.</li>
 * </ul>
 * Після кожного тижня (і після базової мережі) — тональність і вектори нових постів, знімок ехо-камер.
 * Оскільки поведінку задано наперед, результат одночасно перевіряє систему: чи виявляє Louvain,
 * що «затягнутий» користувач опинився в цільовій спільноті, і як змінюється echo-score його спільноти.
 *
 * <p>Метрика echo-score накопичувальна (рахується за всіма постами учасників), тож дрейф показника
 * спільноти помітний переважно тоді, коли користувач переходить в іншу спільноту. Поступову зміну самої
 * поведінки показує <b>особиста тематична однорідність</b>: 1 − H(тем постів і вподобань за тиждень) / log₂(кількість тем).
 */
@Service
public class SeedEvolutionService {

    private static final Logger log = LoggerFactory.getLogger(SeedEvolutionService.class);

    /** Крок зміщення інтересів і тону ролі за тиждень (частка відстані до цілі). */
    private static final double SHIFT = 0.4;
    /** Нових підписок за тиждень у ролях затягування, виходу й міграції. */
    private static final int NEW_FOLLOWS = 3;
    /** Імовірність, що учасник цільової спільноти підпишеться у відповідь. */
    private static final double FOLLOW_BACK = 0.5;
    /** Частка «старих» підписок, від яких роль відписується за тиждень. */
    private static final double UNFOLLOW_SHARE = 0.3;
    /** Імовірність, що колишній читач відпишеться від того, хто змінив інтереси або згасає. */
    private static final double LOST_FOLLOWER = 0.15;
    /** У скільки разів за тиждень падає активність того, хто йде. */
    private static final double FADE = 0.4;
    /** Звичайні користувачі щотижня додають підписок: частка від avgFollows. */
    private static final double REGULAR_FOLLOW_GROWTH = 0.08;
    /** Скільки щонайменше дій (пости + вподобання) потрібно для особистої однорідності. */
    private static final int MIN_ACTIONS = 3;
    /** Скількох звичайних користувачів брати для траєкторій — щоб не опитувати всю мережу. */
    private static final int REGULAR_SAMPLE = 30;

    public enum Persona {
        REGULAR("Звичайні користувачі"),
        DRIFTER("Затягування в ехо-камеру"),
        ESCAPER("Вихід з ехо-камери"),
        MIGRANT("Міграція між спільнотами"),
        NEWCOMER("Новачки"),
        LEAVER("Ті, хто йде");

        private final String title;

        Persona(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Параметри: базова мережа, кількість і тривалість тижнів, частки ролей (від кількості користувачів). */
    public record Params(SeedGeneratorService.Params network, int epochs, int epochDays,
                         double drifterShare, double escaperShare, double migrantShare,
                         double newcomerShare, double leaverShare) {

        void validate() {
            if (epochs < 1 || epochs > 12) throw new BadRequestException("epochs: від 1 до 12");
            if (epochDays < 1 || epochDays > 30) throw new BadRequestException("epochDays: від 1 до 30");
            double sum = 0;
            for (double share : new double[]{drifterShare, escaperShare, migrantShare, newcomerShare, leaverShare}) {
                if (share < 0 || share > 0.3) throw new BadRequestException("частки ролей — від 0 до 0.3");
                sum += share;
            }
            if (sum > 0.6) throw new BadRequestException("разом ролі — щонайбільше 60% користувачів");
        }
    }

    /** Що потрібно від решти системи: NLP і знімок після тижня, траєкторії, поточні спільноти. */
    public interface Observer {
        SnapshotInfo afterEpoch(int epoch, long adminId);

        List<Point> trajectory(long userId);

        /** userId → номер спільноти Louvain з останнього визначення спільнот. */
        Map<Long, Long> communities();
    }

    public record SnapshotInfo(long snapshotId, int communityCount, int userCount,
                               double weightedEchoScore, double maxEchoScore) {
    }

    public record Point(long snapshotId, long community, int memberCount, double echoScore) {
    }

    /** Роль користувача: звідки (спільнота генератора) і куди рухається; новачок до приходу — без user. */
    private static final class Role {
        final Persona persona;
        final int from;
        Integer to;
        int startEpoch;
        Map<Integer, Double> spread;   // нові інтереси того, хто виходить з ехо-камери
        SeedUser user;

        Role(Persona persona, SeedUser user, int from) {
            this.persona = persona;
            this.user = user;
            this.from = from;
        }
    }

    private static final class State {
        final Params params;
        final Network net;
        final List<Role> roles = new ArrayList<>();
        final Map<Long, Role> roleByUser = new HashMap<>();
        final Map<Long, SeedUser> byId = new HashMap<>();
        final Map<Long, SeedPost> postById = new HashMap<>();
        final List<SeedUser> regularSample = new ArrayList<>();
        /** [тиждень][роль] → особисті однорідності й кількості дій. */
        final List<Map<Persona, List<Double>>> personal = new ArrayList<>();
        final List<Map<Persona, List<Integer>>> actions = new ArrayList<>();

        State(Params params, Network net) {
            this.params = params;
            this.net = net;
            net.users.forEach(u -> byId.put(u.id, u));
            net.posts.forEach(post -> postById.put(post.id(), post));
        }

        Persona persona(SeedUser u) {
            Role role = roleByUser.get(u.id);
            return role == null ? Persona.REGULAR : role.persona;
        }

        List<SeedUser> members(int community) {
            return net.users.stream()
                    .filter(u -> u.community == community && !roleByUser.containsKey(u.id))
                    .toList();
        }
    }

    private final SeedGeneratorService generator;
    private final TransactionTemplate tx;
    private final Observer observer;

    public SeedEvolutionService(SeedGeneratorService generator, TransactionTemplate tx, Observer observer) {
        this.generator = generator;
        this.tx = tx;
        this.observer = observer;
    }

    /**
     * Базова мережа + {@code epochs} тижнів еволюції.
     *
     * @param snapshots робити знімки ехо-камер (потрібні Neo4j і NLP-сервіс); false — лише дані
     */
    public Map<String, Object> run(Params p, long adminId, boolean snapshots) {
        p.validate();
        long started = System.nanoTime();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime baseEnd = now.minusDays((long) p.epochs() * p.epochDays());

        State st = tx.execute(status -> {
            State s = new State(p, generator.buildNetwork(p.network(), baseEnd));
            assignRoles(s);
            saveRoles(s);
            // для порівнянності з тижнями — лише останній тиждень базової мережі
            recordPersonal(s, personalTopicsOfBase(s, baseEnd.minusDays(p.epochDays())));
            return s;
        });
        Map<String, Object> base = generator.summarize(st.net);
        base.remove("next");
        log.info("Еволюція: базова мережа {} користувачів, ролей {}", st.net.users.size(), st.roles.size());

        List<Map<String, Object>> epochs = new ArrayList<>();
        List<SnapshotInfo> snapshotByEpoch = new ArrayList<>();
        Map<Long, Long> startCommunities = null;
        String snapshotError = null;

        for (int e = 0; e <= p.epochs(); e++) {
            LocalDateTime from = baseEnd.plusDays((long) (e - 1) * p.epochDays());
            LocalDateTime to = baseEnd.plusDays((long) e * p.epochDays());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("epoch", e);
            row.put("title", e == 0 ? "Базова мережа" : "Тиждень " + e);
            if (e == 0) {
                row.put("to", baseEnd.toString());
                row.put("activity", Map.of("users", st.net.users.size(), "posts", st.net.posts.size()));
            } else {
                int epoch = e;
                row.put("from", from.toString());
                row.put("to", to.toString());
                row.put("activity", tx.execute(status -> simulateEpoch(st, epoch, from, to)));
            }

            SnapshotInfo snapshot = null;
            if (snapshots && snapshotError == null) {
                try {
                    snapshot = observer.afterEpoch(e, adminId);
                    if (e == 0) {
                        startCommunities = observer.communities();
                    }
                } catch (RuntimeException ex) {
                    snapshotError = ex.getMessage();
                    log.warn("Еволюція: знімок після тижня {} не вдався — далі без знімків: {}", e, snapshotError);
                }
            }
            snapshotByEpoch.add(snapshot);
            row.put("snapshot", snapshot);
            epochs.add(row);
            log.info("Еволюція: {} — {}", row.get("title"), row.get("activity"));
        }

        Map<Long, Long> endCommunities = snapshots && snapshotError == null ? observer.communities() : null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("params", p);
        result.put("base", base);
        result.put("epochs", epochs);
        result.put("personas", describePersonas(st, snapshotByEpoch, startCommunities, endCommunities));
        if (snapshotError != null) {
            result.put("snapshotError", snapshotError);
        }
        result.put("durationSeconds", SeedGeneratorService.round((System.nanoTime() - started) / 1e9));
        return result;
    }

    // ===== Ролі =====

    private void assignRoles(State st) {
        Network net = st.net;
        Random rnd = net.rnd;
        Params p = st.params;
        int n = net.users.size();
        List<Community> narrow = net.communities.stream().filter(Community::narrow).toList();

        List<SeedUser> pool = new ArrayList<>(net.users);
        Collections.shuffle(pool, rnd);
        Predicate<SeedUser> inNarrow = u -> net.communities.get(u.community).narrow();

        if (!narrow.isEmpty()) {
            for (SeedUser u : take(st, pool, count(n, p.drifterShare()), u -> !u.bridge && !inNarrow.test(u))) {
                Role role = addRole(st, Persona.DRIFTER, u);
                role.to = narrow.get(rnd.nextInt(narrow.size())).index();
            }
            for (SeedUser u : take(st, pool, count(n, p.escaperShare()), u -> !u.bridge && inNarrow.test(u))) {
                Role role = addRole(st, Persona.ESCAPER, u);
                role.spread = spreadInterests(st, u.community);
            }
        }
        for (SeedUser u : take(st, pool, count(n, p.migrantShare()), u -> !u.bridge)) {
            Role role = addRole(st, Persona.MIGRANT, u);
            int other = rnd.nextInt(net.communities.size() - 1);
            role.to = other >= u.community ? other + 1 : other;
        }
        for (SeedUser u : take(st, pool, count(n, p.leaverShare()), u -> true)) {
            addRole(st, Persona.LEAVER, u).startEpoch = 1 + rnd.nextInt(Math.max(1, p.epochs() - 1));
        }
        int newcomers = count(n, p.newcomerShare());
        for (int i = 0; i < newcomers; i++) {
            Role role = new Role(Persona.NEWCOMER, null, rnd.nextInt(net.communities.size()));
            role.startEpoch = 1 + rnd.nextInt(Math.max(1, p.epochs() - 1));
            st.roles.add(role);
        }
        pool.stream().filter(u -> !st.roleByUser.containsKey(u.id)).limit(REGULAR_SAMPLE).forEach(st.regularSample::add);
    }

    private static int count(int users, double share) {
        return share <= 0 ? 0 : Math.max(1, (int) Math.round(users * share));
    }

    private static List<SeedUser> take(State st, List<SeedUser> pool, int count, Predicate<SeedUser> eligible) {
        List<SeedUser> chosen = new ArrayList<>();
        for (SeedUser u : pool) {
            if (chosen.size() == count) {
                break;
            }
            if (!st.roleByUser.containsKey(u.id) && eligible.test(u)) {
                chosen.add(u);
                st.roleByUser.put(u.id, null);   // зайнято; роль підставить addRole
            }
        }
        return chosen;
    }

    private static Role addRole(State st, Persona persona, SeedUser u) {
        Role role = new Role(persona, u, u.community);
        st.roles.add(role);
        st.roleByUser.put(u.id, role);
        return role;
    }

    /** Ролі — у seed_ground_truth: під час оцінки виявлення спільнот ті, хто переходить, не враховуються. */
    private void saveRoles(State st) {
        for (Role role : st.roles) {
            if (role.user != null) {
                generator.saveRole(role.user.id, role.persona.key(), role.to);
            }
        }
    }

    /** Чотири різні теми, крім головної теми своєї спільноти, — порівну. */
    private Map<Integer, Double> spreadInterests(State st, int community) {
        Integer main = st.net.communities.get(community).interests().keySet().iterator().next();
        List<Integer> topics = new ArrayList<>();
        for (int t = 0; t < generator.content().topics().size(); t++) {
            if (t != main) {
                topics.add(t);
            }
        }
        Collections.shuffle(topics, st.net.rnd);
        Map<Integer, Double> spread = new LinkedHashMap<>();
        topics.stream().limit(4).forEach(t -> spread.put(t, 0.25));
        return spread;
    }

    // ===== Тиждень =====

    private Map<String, Object> simulateEpoch(State st, int epoch, LocalDateTime from, LocalDateTime to) {
        Network net = st.net;
        Random rnd = net.rnd;
        SeedGeneratorService.Params np = st.params.network();
        double perDay = st.params.epochDays() / (double) np.days();
        Counts c = new Counts();
        Map<Long, List<Integer>> topics = new HashMap<>();

        // 1. Новачки
        for (Role role : st.roles) {
            if (role.persona == Persona.NEWCOMER && role.startEpoch == epoch) {
                Community community = net.communities.get(role.from);
                SeedUser u = generator.insertUser(net, community,
                        r -> from.plusMinutes(r.nextInt(st.params.epochDays() * 24 * 60)));
                net.users.add(u);
                st.byId.put(u.id, u);
                role.user = u;
                st.roleByUser.put(u.id, role);
                generator.saveRole(u.id, role.persona.key(), null);
                c.newUsers++;
                int first = Math.max(2, (int) Math.round(np.avgFollows() * 0.6));
                c.follows += followSome(st, u, st.members(role.from), first, false, u.createdAt, to);
            }
        }

        // 2. Зміна поведінки ролей і підписок
        for (Role role : st.roles) {
            SeedUser u = role.user;
            if (u == null) {
                continue;
            }
            switch (role.persona) {
                case DRIFTER, MIGRANT -> {
                    Community target = net.communities.get(role.to);
                    double shift = role.persona == Persona.MIGRANT ? SHIFT * 1.25 : SHIFT;
                    shiftInterests(u.interests, target.interests(), shift);
                    u.bias += shift * (target.bias() - u.bias);
                    c.follows += followSome(st, u, st.members(role.to), NEW_FOLLOWS, true, from, to);
                    if (epoch >= 2 || role.persona == Persona.MIGRANT) {
                        c.unfollows += unfollowSome(st, u, v -> v.community != role.to, UNFOLLOW_SHARE);
                        c.unfollows += loseFollowers(st, u, v -> v.community == role.from);
                    }
                }
                case ESCAPER -> {
                    shiftInterests(u.interests, role.spread, SHIFT);
                    u.bias += SHIFT * (0.0 - u.bias);
                    List<SeedUser> outside = net.users.stream()
                            .filter(v -> v.community != role.from && !st.roleByUser.containsKey(v.id)).toList();
                    c.follows += followSome(st, u, outside, NEW_FOLLOWS, true, from, to);
                    c.unfollows += unfollowSome(st, u, v -> v.community == role.from, UNFOLLOW_SHARE);
                    c.unfollows += loseFollowers(st, u, v -> v.community == role.from);
                }
                case LEAVER -> {
                    if (epoch >= role.startEpoch) {
                        u.activity *= FADE;
                        c.unfollows += unfollowSome(st, u, v -> true, 0.5);
                        c.unfollows += loseFollowers(st, u, v -> true);
                    }
                }
                default -> {
                }
            }
        }
        // звичайні користувачі (і новачки після першого тижня) потроху додають підписок, як у базовій мережі
        for (SeedUser u : new ArrayList<>(net.users)) {
            Persona persona = st.persona(u);
            Role role = st.roleByUser.get(u.id);
            boolean regularLike = persona == Persona.REGULAR
                    || (persona == Persona.NEWCOMER && role.startEpoch < epoch);
            if (!regularLike) {
                continue;
            }
            int extra = SeedGeneratorService.poisson(np.avgFollows() * REGULAR_FOLLOW_GROWTH * u.activity, rnd);
            if (extra > 0) {
                List<SeedUser> pool = rnd.nextDouble() < np.homophily() ? st.members(u.community) : net.users;
                c.follows += followSome(st, u, pool, extra, false, from, to);
            }
        }

        // 3. Пости — у межах тижня, не раніше реєстрації
        List<Object[]> postTags = new ArrayList<>();
        for (SeedUser u : net.users) {
            int count = Math.min(20, SeedGeneratorService.poisson(np.avgPosts() * perDay * u.activity, rnd));
            LocalDateTime start = u.createdAt.isAfter(from) ? u.createdAt : from;
            for (int i = 0; i < count; i++) {
                SeedPost post = generator.insertPost(net, u, r -> SeedGeneratorService.between(start, to, r), postTags);
                st.postById.put(post.id(), post);
                topics.computeIfAbsent(u.id, k -> new ArrayList<>()).add(post.topic());
                c.posts++;
            }
        }
        generator.insertPostTags(postTags);

        // 4. Вподобання й коментарі — серед свіжих постів (цей і попередній тиждень)
        LocalDateTime fresh = from.minusDays(st.params.epochDays());
        List<SeedPost> recent = net.posts.stream().filter(post -> !post.at().isBefore(fresh)).toList();
        Map<Integer, List<SeedPost>> byTopic = new HashMap<>();
        Map<Long, List<SeedPost>> byAuthor = new HashMap<>();
        SeedGeneratorService.groupPosts(recent, byTopic, byAuthor);

        InteractionBatch batch = new InteractionBatch();
        for (SeedUser u : net.users) {
            int target = Math.min(100, SeedGeneratorService.poisson(np.avgLikes() * perDay * u.activity, rnd));
            int before = batch.likes.size();
            generator.interact(net, u, target, byTopic, byAuthor, to, batch);
            for (Object[] like : batch.likes.subList(before, batch.likes.size())) {
                topics.computeIfAbsent(u.id, k -> new ArrayList<>()).add(st.postById.get((Long) like[1]).topic());
            }
        }
        Map<String, Integer> interactions = generator.flushInteractions(batch);

        recordPersonal(st, topics);

        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("users", net.users.size());
        activity.put("activeUsers", topics.size());
        activity.put("newUsers", c.newUsers);
        activity.put("posts", c.posts);
        activity.putAll(interactions);
        activity.put("follows", c.follows);
        activity.put("unfollows", c.unfollows);
        return activity;
    }

    private static final class Counts {
        int newUsers;
        int posts;
        int follows;
        int unfollows;
    }

    /** До {@code count} нових підписок u на користувачів з pool (популярніших — частіше), можливо взаємних. */
    private int followSome(State st, SeedUser u, List<SeedUser> pool, int count, boolean followBack,
                           LocalDateTime from, LocalDateTime to) {
        Random rnd = st.net.rnd;
        if (pool.isEmpty()) {
            return 0;
        }
        int made = 0;
        int added = 0;
        for (int attempt = 0; attempt < count * 6 && added < count; attempt++) {
            SeedUser target = SeedGeneratorService.preferential(pool, st.net.indegree, rnd);
            LocalDateTime start = u.createdAt.isAfter(from) ? u.createdAt : from;
            if (!generator.follow(st.net, u, target, SeedGeneratorService.between(start, to, rnd))) {
                continue;
            }
            added++;
            made++;
            if (followBack && rnd.nextDouble() < FOLLOW_BACK
                    && generator.follow(st.net, target, u, SeedGeneratorService.between(start, to, rnd))) {
                made++;
            }
        }
        return made;
    }

    /** Відписатися від частки {@code share} тих, кого u читає і хто підпадає під {@code which}. */
    private int unfollowSome(State st, SeedUser u, Predicate<SeedUser> which, double share) {
        int removed = 0;
        for (Long id : new ArrayList<>(u.following)) {
            SeedUser target = st.byId.get(id);
            if (which.test(target) && st.net.rnd.nextDouble() < share && generator.unfollow(st.net, u, target)) {
                removed++;
            }
        }
        return removed;
    }

    /** Частина читачів (з {@code who}) відписується від u: людина змінилась або зникла зі стрічки. */
    private int loseFollowers(State st, SeedUser u, Predicate<SeedUser> who) {
        int removed = 0;
        for (SeedUser v : st.net.users) {
            if (v.following.contains(u.id) && who.test(v) && !st.roleByUser.containsKey(v.id)
                    && st.net.rnd.nextDouble() < LOST_FOLLOWER && generator.unfollow(st.net, v, u)) {
                removed++;
            }
        }
        return removed;
    }

    /** interests ← (1 − α)·interests + α·target; дрібні залишки (< 1%) відкидаються. */
    static void shiftInterests(Map<Integer, Double> interests, Map<Integer, Double> target, double alpha) {
        double targetSum = target.values().stream().mapToDouble(Double::doubleValue).sum();
        interests.replaceAll((t, w) -> w * (1 - alpha));
        target.forEach((t, w) -> interests.merge(t, alpha * w / targetSum, Double::sum));
        interests.values().removeIf(w -> w < 0.01);
        SeedGeneratorService.normalize(interests);
    }

    // ===== Особиста однорідність =====

    /**
     * Теми постів і вподобань базової мережі за останній тиждень перед стартом еволюції
     * (вподобання — постів цього тижня: у генераторі їх ставлять протягом кількох днів після публікації).
     */
    private Map<Long, List<Integer>> personalTopicsOfBase(State st, LocalDateTime since) {
        Map<Long, List<Integer>> topics = new HashMap<>();
        for (SeedPost post : st.net.posts) {
            if (!post.at().isBefore(since)) {
                topics.computeIfAbsent(post.authorId(), k -> new ArrayList<>()).add(post.topic());
            }
        }
        for (SeedUser u : st.net.users) {
            for (Long postId : u.liked) {
                SeedPost post = st.postById.get(postId);
                if (!post.at().isBefore(since)) {
                    topics.computeIfAbsent(u.id, k -> new ArrayList<>()).add(post.topic());
                }
            }
        }
        return topics;
    }

    private void recordPersonal(State st, Map<Long, List<Integer>> topicsByUser) {
        int topicCount = generator.content().topics().size();
        Map<Persona, List<Double>> homogeneity = new LinkedHashMap<>();
        Map<Persona, List<Integer>> actions = new LinkedHashMap<>();
        for (Persona persona : Persona.values()) {
            homogeneity.put(persona, new ArrayList<>());
            actions.put(persona, new ArrayList<>());
        }
        for (SeedUser u : st.net.users) {
            Persona persona = st.persona(u);
            List<Integer> topics = topicsByUser.getOrDefault(u.id, List.of());
            actions.get(persona).add(topics.size());
            Double value = personalHomogeneity(topics, topicCount);
            if (value != null) {
                homogeneity.get(persona).add(value);
            }
        }
        st.personal.add(homogeneity);
        st.actions.add(actions);
    }

    /** 1 − H(розподіл тем) / log₂(кількість тем); null — замало дій для оцінки. */
    static Double personalHomogeneity(List<Integer> topics, int topicCount) {
        if (topics.size() < MIN_ACTIONS) {
            return null;
        }
        Map<Integer, Integer> counts = new HashMap<>();
        topics.forEach(t -> counts.merge(t, 1, Integer::sum));
        double entropy = 0;
        for (int count : counts.values()) {
            double p = count / (double) topics.size();
            entropy -= p * Math.log(p) / Math.log(2);
        }
        return 1 - entropy / (Math.log(topicCount) / Math.log(2));
    }

    // ===== Підсумок за ролями =====

    private Map<String, Object> describePersonas(State st, List<SnapshotInfo> snapshots,
                                                 Map<Long, Long> startCommunities, Map<Long, Long> endCommunities) {
        boolean withSnapshots = snapshots.stream().anyMatch(Objects::nonNull);
        Map<Long, Integer> identityStart = startCommunities == null ? Map.of() : identities(st, startCommunities);
        Map<Long, Integer> identityEnd = endCommunities == null ? Map.of() : identities(st, endCommunities);

        Map<String, Object> result = new LinkedHashMap<>();
        for (Persona persona : Persona.values()) {
            List<Role> roles = st.roles.stream().filter(r -> r.persona == persona && r.user != null).toList();
            List<SeedUser> users = persona == Persona.REGULAR ? st.regularSample : roles.stream().map(r -> r.user).toList();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("title", persona.title());
            row.put("count", persona == Persona.REGULAR
                    ? st.net.users.size() - st.roleByUser.size() : roles.size());
            row.put("users", persona == Persona.REGULAR ? List.of() : roles.stream().map(r -> {
                Map<String, Object> u = new LinkedHashMap<>();
                u.put("userId", r.user.id);
                u.put("username", r.user.username);
                u.put("fromCommunity", r.from);
                u.put("toCommunity", r.to);
                if (r.persona == Persona.NEWCOMER || r.persona == Persona.LEAVER) {
                    u.put("startEpoch", r.startEpoch);
                }
                return u;
            }).toList());
            row.put("personalHomogeneity", st.personal.stream().map(m -> mean(m.get(persona))).toList());
            row.put("actionsPerUser", st.actions.stream()
                    .map(m -> mean(m.get(persona).stream().map(Integer::doubleValue).toList())).toList());

            if (withSnapshots && !users.isEmpty()) {
                Map<Long, Map<Long, Point>> pointsByUser = new LinkedHashMap<>();
                for (SeedUser u : users) {
                    Map<Long, Point> bySnapshot = new HashMap<>();
                    observer.trajectory(u.id).forEach(point -> bySnapshot.put(point.snapshotId(), point));
                    pointsByUser.put(u.id, bySnapshot);
                }
                List<Double> echo = new ArrayList<>();
                List<Double> coverage = new ArrayList<>();
                for (SnapshotInfo snapshot : snapshots) {
                    if (snapshot == null) {
                        echo.add(null);
                        coverage.add(null);
                        continue;
                    }
                    List<Double> scores = pointsByUser.values().stream()
                            .map(m -> m.get(snapshot.snapshotId())).filter(Objects::nonNull)
                            .map(Point::echoScore).toList();
                    echo.add(mean(scores));
                    coverage.add(SeedGeneratorService.round(scores.size() / (double) users.size()));
                }
                row.put("communityEcho", echo);
                row.put("inCommunityShare", coverage);
                row.put("example", example(users.get(0), pointsByUser.get(users.get(0).id), snapshots));
                if (!roles.isEmpty() && endCommunities != null) {
                    row.put("outcome", outcome(persona, roles, startCommunities, endCommunities,
                            identityStart, identityEnd, echo));
                }
            }
            result.put(persona.key(), row);
        }
        return result;
    }

    /**
     * «Чия» це спільнота Louvain: спільнота генератора, до якої належить більшість її звичайних учасників
     * (ролі не враховуються — вони саме й переходять між спільнотами).
     */
    private static Map<Long, Integer> identities(State st, Map<Long, Long> communities) {
        Map<Long, Map<Integer, Integer>> votes = new HashMap<>();
        communities.forEach((userId, label) -> {
            SeedUser u = st.byId.get(userId);
            if (u != null && !st.roleByUser.containsKey(userId)) {
                votes.computeIfAbsent(label, k -> new HashMap<>()).merge(u.community, 1, Integer::sum);
            }
        });
        Map<Long, Integer> identity = new HashMap<>();
        votes.forEach((label, counts) -> identity.put(label,
                Collections.max(counts.entrySet(), Map.Entry.comparingByValue()).getKey()));
        return identity;
    }

    private static Map<String, Object> outcome(Persona persona, List<Role> roles,
                                               Map<Long, Long> start, Map<Long, Long> end,
                                               Map<Long, Integer> identityStart, Map<Long, Integer> identityEnd,
                                               List<Double> echo) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (persona == Persona.DRIFTER || persona == Persona.MIGRANT || persona == Persona.ESCAPER) {
            List<Role> base = roles.stream().filter(r -> start != null && start.containsKey(r.user.id)).toList();
            long startedHome = base.stream()
                    .filter(r -> Objects.equals(identityStart.get(start.get(r.user.id)), r.from)).count();
            long reached = roles.stream().filter(r -> {
                Integer at = identityEnd.get(end.get(r.user.id));
                return persona == Persona.ESCAPER ? !Objects.equals(at, r.from) : Objects.equals(at, r.to);
            }).count();
            row.put("startedInOwnCommunity", share(startedHome, base.size()));
            row.put(persona == Persona.ESCAPER ? "leftOwnCommunity" : "reachedTargetCommunity",
                    share(reached, roles.size()));
        }
        Double first = echo.stream().filter(Objects::nonNull).findFirst().orElse(null);
        Double last = null;
        for (Double value : echo) {
            if (value != null) {
                last = value;
            }
        }
        if (first != null && last != null) {
            row.put("communityEchoChange", SeedGeneratorService.round(last - first));
        }
        return row;
    }

    private static Map<String, Object> example(SeedUser u, Map<Long, Point> points, List<SnapshotInfo> snapshots) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int e = 0; e < snapshots.size(); e++) {
            SnapshotInfo snapshot = snapshots.get(e);
            Point point = snapshot == null ? null : points.get(snapshot.snapshotId());
            if (point == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("epoch", e);
            row.put("snapshotId", point.snapshotId());
            row.put("community", point.community());
            row.put("memberCount", point.memberCount());
            row.put("echoScore", SeedGeneratorService.round(point.echoScore()));
            row.put("networkEchoScore", SeedGeneratorService.round(snapshot.weightedEchoScore()));
            rows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", u.id);
        result.put("username", u.username);
        result.put("points", rows);
        return result;
    }

    private static Double mean(List<Double> values) {
        return values.isEmpty() ? null
                : SeedGeneratorService.round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    private static double share(long part, long total) {
        return total == 0 ? 0 : SeedGeneratorService.round(part / (double) total);
    }
}