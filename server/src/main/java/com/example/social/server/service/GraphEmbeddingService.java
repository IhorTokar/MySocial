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
import com.example.social.shared.dto.EmbeddingResultDto;
import com.example.social.shared.dto.UserEmbeddingDto;

@Service
public class GraphEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(GraphEmbeddingService.class);
    private static final String GRAPH_NAME = "userFollowGraph";

    private final Driver neo4jDriver;

    public GraphEmbeddingService(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    public EmbeddingResultDto generateEmbeddings(int embeddingDimension) {
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
            EmbeddingResultDto result = session.executeWrite(tx -> {
                var res = tx.run(
                        "CALL gds.node2vec.write($name, { " +
                                "  embeddingDimension: $dim, " +
                                "  writeProperty: 'embedding' " +
                                "}) YIELD nodeCount, nodePropertiesWritten",
                        Map.of("name", GRAPH_NAME, "dim", embeddingDimension)
                );
                Record record = res.single();
                return new EmbeddingResultDto(
                        record.get("nodeCount").asLong(),
                        record.get("nodePropertiesWritten").asLong());
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

    public List<UserEmbeddingDto> getEmbeddings() {
        try (Session session = neo4jDriver.session()) {
            List<UserEmbeddingDto> result = session.executeRead(tx -> {
                var res = tx.run(
                        "MATCH (u:User) WHERE u.embedding IS NOT NULL " +
                                "RETURN u.userId AS userId, u.username AS username, u.embedding AS embedding"
                );
                List<UserEmbeddingDto> list = new ArrayList<>();
                for (Record record : res.list()) {
                    Value embeddingValue = record.get("embedding");
                    List<Double> embedding = new ArrayList<>();
                    for (Value v : embeddingValue.values()) {
                        embedding.add(v.asDouble());
                    }
                    list.add(new UserEmbeddingDto(
                            record.get("userId").asLong(),
                            record.get("username").asString(),
                            embedding));
                }
                return list;
            });
            return result;
        }
    }
}