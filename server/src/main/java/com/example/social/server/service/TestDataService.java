package com.example.social.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class TestDataService {

    private static final Logger log = LoggerFactory.getLogger(TestDataService.class);
    private static final String TEST_PASSWORD = "password123";

    private final UserService userService;
    private final FollowersService followersService;
    private final GraphSyncService graphSyncService;

    public TestDataService(UserService userService,
                           FollowersService followersService,
                           GraphSyncService graphSyncService) {
        this.userService = userService;
        this.followersService = followersService;
        this.graphSyncService = graphSyncService;
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
}