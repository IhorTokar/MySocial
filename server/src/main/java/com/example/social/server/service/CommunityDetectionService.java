package com.example.social.server.service;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CommunityDetectionService {

    private static final Logger log = LoggerFactory.getLogger(CommunityDetectionService.class);
    private static final String GRAPH_NAME = "userCommunityGraph";

    private final Driver neo4jDriver;

    public CommunityDetectionService(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    public Map<String, Object> detectCommunities() {
        try (Session session = neo4jDriver.session()) {

            // Прибрати попередню проекцію, якщо лишилась
            session.executeWrite(tx -> {
                tx.run("CALL gds.graph.exists($name) YIELD exists " +
                                "WITH exists WHERE exists = true " +
                                "CALL gds.graph.drop($name) YIELD graphName RETURN graphName",
                        Map.of("name", GRAPH_NAME));
                return null;
            });

            // Louvain працює на неорієнтованому графі — проєктуємо FOLLOWS в обидва боки,
            // інакше алгоритм вважатиме A→B і B→A різними зв'язками і спільноти вийдуть штучно роздроблені
            session.executeWrite(tx -> {
                tx.run("CALL gds.graph.project($name, 'User', { " +
                                "FOLLOWS: { orientation: 'UNDIRECTED' } " +
                                "})",
                        Map.of("name", GRAPH_NAME));
                return null;
            });

            // Запустити Louvain і записати номер спільноти назад у вузли
            Map<String, Object> result = session.executeWrite(tx -> {
                var res = tx.run(
                        "CALL gds.louvain.write($name, { " +
                                "  writeProperty: 'community' " +
                                "}) YIELD communityCount, modularity, nodePropertiesWritten",
                        Map.of("name", GRAPH_NAME)
                );
                Record record = res.single();
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("communityCount", record.get("communityCount").asLong());
                resultMap.put("modularity", record.get("modularity").asDouble());
                resultMap.put("nodePropertiesWritten", record.get("nodePropertiesWritten").asLong());
                return resultMap;
            });

            // Прибрати проекцію з пам'яті
            session.executeWrite(tx -> {
                tx.run("CALL gds.graph.drop($name)", Map.of("name", GRAPH_NAME));
                return null;
            });

            log.info("Louvain community detection completed: {}", result);
            return result;
        }
    }

    public List<Map<String, Object>> getCommunities() {
        try (Session session = neo4jDriver.session()) {
            List<Map<String, Object>> result = session.executeRead(tx -> {
                var res = tx.run(
                        "MATCH (u:User) WHERE u.community IS NOT NULL " +
                                "RETURN u.userId AS userId, u.username AS username, u.community AS community " +
                                "ORDER BY u.community, u.userId"
                );
                List<Map<String, Object>> list = new ArrayList<>();
                for (Record record : res.list()) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("userId", record.get("userId").asLong());
                    entry.put("username", record.get("username").asString());
                    entry.put("community", record.get("community").asLong());
                    list.add(entry);
                }
                return list;
            });
            return result;
        }
    }

    public Map<String, Object> getCommunitySummary() {
        try (Session session = neo4jDriver.session()) {
            List<Map<String, Object>> summary = session.executeRead(tx -> {
                var res = tx.run(
                        "MATCH (u:User) WHERE u.community IS NOT NULL " +
                                "RETURN u.community AS community, count(u) AS size " +
                                "ORDER BY size DESC"
                );
                List<Map<String, Object>> list = new ArrayList<>();
                for (Record record : res.list()) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("community", record.get("community").asLong());
                    entry.put("size", record.get("size").asLong());
                    list.add(entry);
                }
                return list;
            });

            Map<String, Object> response = new HashMap<>();
            response.put("totalCommunities", summary.size());
            response.put("communities", summary);
            return response;
        }
    }
}