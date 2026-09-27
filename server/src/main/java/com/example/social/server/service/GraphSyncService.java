package com.example.social.server.service;

import com.example.social.server.entity.Followers;
import com.example.social.server.entity.User;
import com.example.social.server.repository.FollowersRepository;
import com.example.social.server.repository.UserRepository;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GraphSyncService {

    private static final Logger log = LoggerFactory.getLogger(GraphSyncService.class);

    private final Driver neo4jDriver;
    private final UserRepository userRepository;
    private final FollowersRepository followersRepository;

    public GraphSyncService(Driver neo4jDriver,
                            UserRepository userRepository,
                            FollowersRepository followersRepository) {
        this.neo4jDriver = neo4jDriver;
        this.userRepository = userRepository;
        this.followersRepository = followersRepository;
    }

    /**
     * Синхронізує User-вузли та FOLLOWS-звʼязки з Postgres у Neo4j через MERGE,
     * а НЕ через DELETE+CREATE — це навмисно, щоб зберегти похідні властивості
     * (embedding, community, communityLeiden), записані окремими GDS-викликами.
     * Видаляє лише вузли/звʼязки, яких більше немає в Postgres (юзер видалений,
     * підписка скасована) — це не чіпає властивості вузлів, що лишаються.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> syncAll() {
        List<User> users = userRepository.findAll();
        List<Followers> relations = followersRepository.findAll();

        List<Long> currentUserIds = users.stream().map(User::getUserId).collect(Collectors.toList());

        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                tx.run("CREATE CONSTRAINT user_id_unique IF NOT EXISTS " +
                        "FOR (u:User) REQUIRE u.userId IS UNIQUE");
                return null;
            });

            // Прибрати юзерів, яких більше немає в Postgres (видалені акаунти) —
            // разом з їхніми звʼязками, щоб не лишати "сирітські" вузли
            session.executeWrite(tx -> {
                tx.run("MATCH (u:User) WHERE NOT u.userId IN $currentIds " +
                                "DETACH DELETE u",
                        Map.of("currentIds", currentUserIds));
                return null;
            });

            // MERGE замість CREATE: якщо вузол уже існує — лише оновлюємо username,
            // усі інші властивості (embedding, community тощо) лишаються незмінними
            session.executeWrite(tx -> {
                for (User user : users) {
                    tx.run("MERGE (u:User {userId: $userId}) " +
                                    "SET u.username = $username",
                            Map.of(
                                    "userId", user.getUserId(),
                                    "username", user.getUsername()
                            ));
                }
                return null;
            });

            // Прибрати звʼязки, яких більше немає в Postgres (відписки) —
            // порівнюємо поточний набір пар (follower, following) з тим, що вже в Neo4j
            List<Map<String, Object>> currentPairs = relations.stream()
                    .map(rel -> Map.<String, Object>of(
                            "followerId", rel.getFollower().getUserId(),
                            "followingId", rel.getFollowing().getUserId()))
                    .collect(Collectors.toList());

            session.executeWrite(tx -> {
                tx.run("MATCH (a:User)-[r:FOLLOWS]->(b:User) " +
                                "WHERE NOT [a.userId, b.userId] IN " +
                                "  [pair IN $pairs | [pair.followerId, pair.followingId]] " +
                                "DELETE r",
                        Map.of("pairs", currentPairs));
                return null;
            });

            // Додати нові підписки (MERGE — існуючі звʼязки не дублюються)
            session.executeWrite(tx -> {
                for (Followers rel : relations) {
                    tx.run("MATCH (a:User {userId: $followerId}) " +
                                    "MATCH (b:User {userId: $followingId}) " +
                                    "MERGE (a)-[:FOLLOWS]->(b)",
                            Map.of(
                                    "followerId", rel.getFollower().getUserId(),
                                    "followingId", rel.getFollowing().getUserId()
                            ));
                }
                return null;
            });
        }

        log.info("Graph sync completed (merge mode): {} users, {} follow relations", users.size(), relations.size());

        return Map.of(
                "syncedUsers", users.size(),
                "syncedRelations", relations.size()
        );
    }

    public Map<String, Object> getGraphStats() {
        try (Session session = neo4jDriver.session()) {
            long nodeCount = session.executeRead(tx ->
                    tx.run("MATCH (u:User) RETURN count(u) AS c").single().get("c").asLong());

            long relCount = session.executeRead(tx ->
                    tx.run("MATCH ()-[r:FOLLOWS]->() RETURN count(r) AS c").single().get("c").asLong());

            return Map.of(
                    "userNodes", nodeCount,
                    "followRelations", relCount
            );
        }
    }
}