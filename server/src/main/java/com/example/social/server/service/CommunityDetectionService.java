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
        return runCommunityAlgorithm(
                "gds.louvain.write",
                "community",
                Map.of("writeProperty", "community")
        );
    }

    /**
     * Leiden — вдосконалення Louvain: гарантує, що кожна знайдена спільнота
     * є зв'язним підграфом (Louvain цього не гарантує й іноді дає
     * штучно об'єднані розрізнені кластери). Пишеться в окрему властивість
     * 'communityLeiden', не перезаписуючи результат Louvain — обидва
     * можна тримати одночасно для порівняння.
     */
    public Map<String, Object> detectCommunitiesLeiden() {
        return runCommunityAlgorithm(
                "gds.leiden.write",
                "communityLeiden",
                Map.of("writeProperty", "communityLeiden")
        );
    }

    private Map<String, Object> runCommunityAlgorithm(String procedure, String propertyName, Map<String, Object> config) {
        try (Session session = neo4jDriver.session()) {

            session.executeWrite(tx -> {
                tx.run("CALL gds.graph.exists($name) YIELD exists " +
                                "WITH exists WHERE exists = true " +
                                "CALL gds.graph.drop($name) YIELD graphName RETURN graphName",
                        Map.of("name", GRAPH_NAME));
                return null;
            });

            // Louvain і Leiden обидва працюють на неорієнтованому графі —
            // проєктуємо FOLLOWS в обидва боки, інакше A→B і B→A вважаються
            // різними зв'язками і спільноти виходять штучно роздроблені
            session.executeWrite(tx -> {
                tx.run("CALL gds.graph.project($name, 'User', { " +
                                "FOLLOWS: { orientation: 'UNDIRECTED' } " +
                                "})",
                        Map.of("name", GRAPH_NAME));
                return null;
            });

            Map<String, Object> result = session.executeWrite(tx -> {
                var res = tx.run(
                        "CALL " + procedure + "($name, $config) " +
                                "YIELD communityCount, modularity, nodePropertiesWritten",
                        Map.of("name", GRAPH_NAME, "config", config)
                );
                Record record = res.single();
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("algorithm", propertyName);
                resultMap.put("communityCount", record.get("communityCount").asLong());
                resultMap.put("modularity", record.get("modularity").asDouble());
                resultMap.put("nodePropertiesWritten", record.get("nodePropertiesWritten").asLong());
                return resultMap;
            });

            session.executeWrite(tx -> {
                tx.run("CALL gds.graph.drop($name)", Map.of("name", GRAPH_NAME));
                return null;
            });

            log.info("{} community detection completed: {}", propertyName, result);
            return result;
        }
    }

    public List<Map<String, Object>> getCommunities() {
        return getCommunitiesByProperty("community");
    }

    public List<Map<String, Object>> getCommunitiesLeiden() {
        return getCommunitiesByProperty("communityLeiden");
    }

    private List<Map<String, Object>> getCommunitiesByProperty(String propertyName) {
        try (Session session = neo4jDriver.session()) {
            List<Map<String, Object>> result = session.executeRead(tx -> {
                var res = tx.run(
                        "MATCH (u:User) WHERE u." + propertyName + " IS NOT NULL " +
                                "RETURN u.userId AS userId, u.username AS username, u." + propertyName + " AS community " +
                                "ORDER BY u." + propertyName + ", u.userId"
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
        return getCommunitySummaryByProperty("community");
    }

    public Map<String, Object> getCommunitySummaryLeiden() {
        return getCommunitySummaryByProperty("communityLeiden");
    }

    private Map<String, Object> getCommunitySummaryByProperty(String propertyName) {
        try (Session session = neo4jDriver.session()) {
            List<Map<String, Object>> summary = session.executeRead(tx -> {
                var res = tx.run(
                        "MATCH (u:User) WHERE u." + propertyName + " IS NOT NULL " +
                                "RETURN u." + propertyName + " AS community, count(u) AS size " +
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

    /**
     * Перевіряє зв'язність кожної спільноти, знайденої за вказаною властивістю
     * (community/communityLeiden): для кожної спільноти проєктує її підграф
     * і рахує кількість weakly connected components (WCC). Якщо componentCount > 1,
     * спільнота фактично складається з кількох незв'язаних кластерів, помилково
     * об'єднаних алгоритмом — це відома слабкість Louvain, яку Leiden гарантовано усуває.
     */
    /**
     * Перевіряє зв'язність кожної спільноти, знайденої за вказаною властивістю
     * (community/communityLeiden): бере довільний вузол спільноти як стартовий,
     * обходить FOLLOWS в обох напрямках (variable-length path) у межах ТІЄЇ Ж
     * спільноти, і рахує, скільки учасників реально досяжні. Якщо досяжних менше,
     * ніж загальний розмір спільноти — вона фактично складається з кількох
     * незв'язаних кластерів, помилково об'єднаних алгоритмом. Реалізовано через
     * чистий Cypher (без GDS-проєкції довільного списку вузлів, яку gds.graph.project
     * не підтримує для колекції NodeEntity).
     */
    public List<Map<String, Object>> checkConnectivity(String propertyName) {
        try (Session session = neo4jDriver.session()) {
            List<Long> communityIds = session.executeRead(tx -> {
                var res = tx.run(
                        "MATCH (u:User) WHERE u." + propertyName + " IS NOT NULL " +
                                "RETURN DISTINCT u." + propertyName + " AS community"
                );
                List<Long> ids = new ArrayList<>();
                for (Record record : res.list()) {
                    ids.add(record.get("community").asLong());
                }
                return ids;
            });

            List<Map<String, Object>> results = new ArrayList<>();

            for (Long communityId : communityIds) {
                long communitySize = session.executeRead(tx ->
                        tx.run("MATCH (u:User) WHERE u." + propertyName + " = $communityId " +
                                                "RETURN count(u) AS c",
                                        Map.of("communityId", communityId))
                                .single().get("c").asLong());

                // Кількість учасників, реально досяжних з довільного вузла спільноти
                // через FOLLOWS у будь-якому напрямку, не виходячи за межі тієї ж спільноти
                long reachableCount = session.executeRead(tx ->
                        tx.run(
                                "MATCH (start:User {" + propertyName + ": $communityId}) " +
                                        "WITH start LIMIT 1 " +
                                        "MATCH (start)-[:FOLLOWS*0..]-(reached:User) " +
                                        "WHERE reached." + propertyName + " = $communityId " +
                                        "RETURN count(DISTINCT reached) AS c",
                                Map.of("communityId", communityId)
                        ).single().get("c").asLong());

                Map<String, Object> entry = new HashMap<>();
                entry.put("community", communityId);
                entry.put("communitySize", communitySize);
                entry.put("reachableFromOneNode", reachableCount);
                entry.put("isFullyConnected", reachableCount == communitySize);
                results.add(entry);
            }

            return results;
        }
    }

    public List<Map<String, Object>> checkConnectivityLouvain() {
        return checkConnectivity("community");
    }

    public List<Map<String, Object>> checkConnectivityLeiden() {
        return checkConnectivity("communityLeiden");
    }

}