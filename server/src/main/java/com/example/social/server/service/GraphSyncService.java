package com.example.social.server.service;

import com.example.social.server.entity.Followers;
import com.example.social.server.entity.User;
import com.example.social.server.repository.FollowersRepository;
import com.example.social.server.repository.UserRepository;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

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

    @Transactional(readOnly = true)
    public Map<String, Object> syncAll() {
        List<User> users = userRepository.findAll();
        List<Followers> relations = followersRepository.findAll();

        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                tx.run("MATCH (n:User) DETACH DELETE n");
                return null;
            });

            session.executeWrite(tx -> {
                tx.run("CREATE CONSTRAINT user_id_unique IF NOT EXISTS " +
                        "FOR (u:User) REQUIRE u.userId IS UNIQUE");
                return null;
            });

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

        log.info("Graph sync completed: {} users, {} follow relations", users.size(), relations.size());

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