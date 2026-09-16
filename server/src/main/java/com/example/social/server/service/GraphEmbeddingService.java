package com.example.social.server.service;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GraphEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(GraphEmbeddingService.class);
    private static final String GRAPH_NAME = "userFollowGraph";

    private final Driver neo4jDriver;

    public GraphEmbeddingService(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    public Map<String, Object> generateEmbeddings(int embeddingDimension) {
        try (Session session = neo4jDriver.session()) {

            // Прибрати попередню проекцію, якщо лишилась від минулого разу
            session.executeWrite(tx -> {
                tx.run("CALL gds.graph.exists($name) YIELD exists " +
                                "WITH exists WHERE exists = true " +
                                "CALL gds.graph.drop($name) YIELD graphName RETURN graphName",
                        Map.of("name", GRAPH_NAME));
                return null;
            });

            // Спроєктувати граф підписок у пам'ять GDS
            session.executeWrite(tx -> {
                tx.run("CALL gds.graph.project($name, 'User', 'FOLLOWS')",
                        Map.of("name", GRAPH_NAME));
                return null;
            });

            // Запустити node2vec і записати ембединги назад як властивість вузлів
            Map<String, Object> result = session.executeWrite(tx -> {
                var res = tx.run(
                        "CALL gds.node2vec.write($name, { " +
                                "  embeddingDimension: $dim, " +
                                "  writeProperty: 'embedding' " +
                                "}) YIELD nodeCount, nodePropertiesWritten",
                        Map.of("name", GRAPH_NAME, "dim", embeddingDimension)
                );
                Record record = res.single();
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("nodeCount", record.get("nodeCount").asLong());
                resultMap.put("nodePropertiesWritten", record.get("nodePropertiesWritten").asLong());
                return resultMap;
            });

            // Прибрати проекцію з пам'яті після завершення (дані вже записані у вузли)
            session.executeWrite(tx -> {
                tx.run("CALL gds.graph.drop($name)", Map.of("name", GRAPH_NAME));
                return null;
            });

            log.info("node2vec embeddings generated: {}", result);
            return result;
        }
    }

    public List<Map<String, Object>> getEmbeddings() {
        try (Session session = neo4jDriver.session()) {
            List<Map<String, Object>> result = session.executeRead(tx -> {
                var res = tx.run(
                        "MATCH (u:User) WHERE u.embedding IS NOT NULL " +
                                "RETURN u.userId AS userId, u.username AS username, u.embedding AS embedding"
                );
                List<Map<String, Object>> list = new ArrayList<>();
                for (Record record : res.list()) {
                    Value embeddingValue = record.get("embedding");
                    List<Double> embedding = new ArrayList<>();
                    for (Value v : embeddingValue.values()) {
                        embedding.add(v.asDouble());
                    }
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("userId", record.get("userId").asLong());
                    entry.put("username", record.get("username").asString());
                    entry.put("embedding", embedding);
                    list.add(entry);
                }
                return list;
            });
            return result;
        }
    }
}