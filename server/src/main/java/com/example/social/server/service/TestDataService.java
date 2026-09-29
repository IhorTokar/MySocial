package com.example.social.server.service;

import com.example.social.server.entity.Post;
import com.example.social.server.entity.PostLike;
import com.example.social.server.entity.Tag;
import com.example.social.server.entity.User;
import com.example.social.server.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Collectors;

import java.util.*;

@Service
public class TestDataService {

    private static final Logger log = LoggerFactory.getLogger(TestDataService.class);
    private static final String TEST_PASSWORD = "password123";

    private static final String[] TAG_POOL = {
            "технології", "подорожі", "їжа", "спорт", "музика", "кіно", "наука",
            "природа", "мистецтво", "ігри", "книги", "мода", "фотографія",
            "авто", "здоровя", "бізнес", "програмування", "фітнес", "дизайн", "меми"
    };

    private static final String[] WORD_POOL = {
            "сьогодні", "думаю", "що", "це", "дуже", "цікаво", "важливо", "круто",
            "незвичайно", "варто", "спробувати", "подивитись", "дізнатись", "більше",
            "про", "такі", "речі", "в", "нашому", "житті", "адже", "вони", "формують",
            "досвід", "кожного", "дня", "і", "надихають", "на", "нові", "звершення",
            "цей", "момент", "запамятався", "мені", "надовго", "раджу", "всім"
    };

    private static final String[] NICK_ADJECTIVES = {
            "Тихий", "Швидкий", "Хитрий", "Сонний", "Веселий", "Дикий", "Спокійний",
            "Гострий", "Яскравий", "Тінистий", "Мудрий", "Сміливий", "Похмурий",
            "Легкий", "Гучний", "Північний", "Південний", "Холодний", "Теплий", "Зоряний"
    };

    private static final String[] NICK_NOUNS = {
            "Вовк", "Лис", "Сокіл", "Ведмідь", "Тигр", "Орел", "Змій", "Кіт",
            "Яструб", "Барс", "Крук", "Олень", "Рись", "Койот", "Шакал",
            "Дракон", "Фенікс", "Грифон", "Тінь", "Шторм"
    };

    private final UserService userService;
    private final FollowersService followersService;
    private final GraphSyncService graphSyncService;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final TagRepository tagRepository;
    private final PostLikeRepository postLikeRepository;
    private final EmbeddingApiService embeddingApiService;
    private final PostEmbeddingRepository postEmbeddingRepository;

    public TestDataService(UserService userService,
                           FollowersService followersService,
                           GraphSyncService graphSyncService,
                           UserRepository userRepository,
                           PostRepository postRepository,
                           TagRepository tagRepository,
                           PostLikeRepository postLikeRepository,
                           EmbeddingApiService embeddingApiService,
                           PostEmbeddingRepository postEmbeddingRepository) {
        this.userService = userService;
        this.followersService = followersService;
        this.graphSyncService = graphSyncService;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.tagRepository = tagRepository;
        this.postLikeRepository = postLikeRepository;
        this.embeddingApiService = embeddingApiService;
        this.postEmbeddingRepository = postEmbeddingRepository;
    }

    public Map<String, Object> generateTestData(int userCount, int maxFollowsPerUser) {
        Random random = new Random();
        List<Long> createdUserIds = new ArrayList<>();

        // 1. Створити N тестових користувачів
        for (int i = 1; i <= userCount; i++) {
            String username = "seed_user_" + System.currentTimeMillis() + "_" + i;
            String email = username + "@example.com";
            try {
                var user = userService.registerUser(username, email, TEST_PASSWORD);
                createdUserIds.add(user.getUserId());
            } catch (IllegalArgumentException e) {
                log.warn("Skipped user {}: {}", username, e.getMessage());
            }
        }

        // 2. Створити випадкові підписки між ними
        int followsCreated = 0;
        for (Long followerId : createdUserIds) {
            int followCount = 1 + random.nextInt(maxFollowsPerUser);

            for (int j = 0; j < followCount; j++) {
                Long followingId = createdUserIds.get(random.nextInt(createdUserIds.size()));

                if (followingId.equals(followerId)) {
                    continue;
                }

                try {
                    followersService.follow(followerId, followingId);
                    followsCreated++;
                } catch (IllegalArgumentException e) {
                    // вже підписаний — нормально, просто пропускаємо
                }
            }
        }

        // 3. Синхронізувати оновлений граф у Neo4j
        Map<String, Object> syncResult = graphSyncService.syncAll();

        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("usersCreated", createdUserIds.size());
        summary.put("followsCreated", followsCreated);
        summary.put("graphSync", syncResult);

        log.info("Test data generated: {} users, {} follows", createdUserIds.size(), followsCreated);
        return summary;
    }

    @Transactional
    public Map<String, Object> generateRandomPosts(int count) {
        List<User> allUsers = userRepository.findAll();
        if (allUsers.isEmpty()) {
            throw new IllegalStateException("Немає жодного користувача — спершу викличте generateTestData");
        }

        List<Tag> tagPool = ensureTagPool();
        Random random = new Random();
        int created = 0;

        for (int i = 0; i < count; i++) {
            Post post = new Post();
            post.setUser(allUsers.get(random.nextInt(allUsers.size())));
            post.setLabel("Випадковий пост #" + (i + 1));
            post.setText(generateRandomText(random));
            post.setTags(pickRandomTags(tagPool, random));

            postRepository.save(post);
            created++;
        }

        log.info("Generated {} random posts with random tags", created);
        return Map.of("postsCreated", created);
    }

    private String generateRandomText(Random random) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 50) {
            sb.append(WORD_POOL[random.nextInt(WORD_POOL.length)]).append(" ");
        }
        return sb.toString().trim() + ".";
    }

    private Set<Tag> pickRandomTags(List<Tag> tagPool, Random random) {
        int tagCount = 1 + random.nextInt(3); // 1–3 теги на пост
        Set<Tag> tags = new HashSet<>();
        while (tags.size() < tagCount) {
            tags.add(tagPool.get(random.nextInt(tagPool.size())));
        }
        return tags;
    }

    private List<Tag> ensureTagPool() {
        List<Tag> tags = new ArrayList<>();
        for (String name : TAG_POOL) {
            Tag tag = tagRepository.findByName(name)
                    .orElseGet(() -> {
                        Tag t = new Tag();
                        t.setName(name);
                        return tagRepository.save(t);
                    });
            tags.add(tag);
        }
        return tags;
    }

    @Transactional
    public Map<String, Object> generateRandomLikes(int count) {
        List<User> allUsers = userRepository.findAll();
        List<Post> allPosts = postRepository.findAll();

        if (allUsers.isEmpty() || allPosts.isEmpty()) {
            throw new IllegalStateException("Потрібні користувачі й пости — спершу /seed і /seed-posts");
        }

        Random random = new Random();
        int created = 0;
        int skipped = 0;

        for (int i = 0; i < count; i++) {
            User user = allUsers.get(random.nextInt(allUsers.size()));
            Post post = allPosts.get(random.nextInt(allPosts.size()));

            if (postLikeRepository.existsByUserAndPost(user, post)) {
                skipped++;
                continue;
            }

            PostLike like = new PostLike();
            like.setUser(user);
            like.setPost(post);
            postLikeRepository.save(like);
            created++;
        }

        log.info("Generated {} random likes ({} skipped as duplicates)", created, skipped);
        return Map.of("likesCreated", created, "duplicatesSkipped", skipped);
    }

    @Transactional
    public Map<String, Object> backfillEmbeddings() {
        List<Post> postsWithoutEmbedding = postRepository.findByEmbeddingIsNull();

        int processed = 0;
        int failed = 0;

        for (Post post : postsWithoutEmbedding) {
            List<Double> vector = embeddingApiService.embed(post.getText());
            if (vector != null) {
                postEmbeddingRepository.saveEmbedding(post.getPostId(), vector);
                processed++;
            } else {
                failed++;
            }
        }

        log.info("Backfill complete: {} embedded, {} failed", processed, failed);
        return Map.of("processed", processed, "failed", failed, "total", postsWithoutEmbedding.size());
    }

    /**
     * Замінює технічні seed_user_... нікнейми на випадкові читабельні,
     * не чіпаючи реальні тестові акаунти (test_user, second_user тощо).
     */
    @Transactional
    public Map<String, Object> randomizeSeedUsernames() {
        List<User> users = userRepository.findAll().stream()
                .filter(u -> u.getUsername() != null && u.getUsername().startsWith("seed_user_"))
                .collect(Collectors.toList());

        Random random = new Random();
        int renamed = 0;

        for (User user : users) {
            String newUsername = null;
            for (int attempt = 0; attempt < 20; attempt++) {
                String candidate = NICK_ADJECTIVES[random.nextInt(NICK_ADJECTIVES.length)]
                        + NICK_NOUNS[random.nextInt(NICK_NOUNS.length)]
                        + random.nextInt(1000);
                if (!userRepository.existsByUsername(candidate)) {
                    newUsername = candidate;
                    break;
                }
            }
            if (newUsername == null) {
                log.warn("Could not find free nickname for user {}", user.getUserId());
                continue;
            }

            user.setUsername(newUsername);
            userRepository.save(user);
            renamed++;
        }

        log.info("Renamed {} seed usernames", renamed);
        return Map.of("usersRenamed", renamed, "totalCandidates", users.size());
    }
}