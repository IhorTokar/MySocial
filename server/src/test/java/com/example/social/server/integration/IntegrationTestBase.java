package com.example.social.server.integration;

import com.example.social.server.entity.User;
import com.example.social.server.entity.UserPrivate;
import com.example.social.server.entity.UserRole;
import com.example.social.server.repository.UserPrivateRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.server.security.JwtTokenProvider;
import com.example.social.server.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Основа інтеграційних тестів: повний Spring-контекст (фільтри безпеки, контролери, сервіси, Flyway)
 * і окрема тестова PostgreSQL у Docker. Робоча база з docker-compose не використовується й не змінюється.
 *
 * Neo4j і NLP-сервіс тут не потрібні: драйвер Neo4j не з'єднується, поки його не викликали,
 * а ендпоінти, які перевіряють тести, до графа й NLP не звертаються.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    /** Той самий образ, що в docker-compose: з розширенням pgvector, яке потрібне міграції V3. */
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        // Один контейнер на всі тестові класи: стартує один раз, зупиняється разом з JVM
        POSTGRES.start();
    }

    protected static final String PASSWORD = "password123";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("jwt.secret", () -> "integration-test-secret-key-longer-than-32-bytes");
        // Neo4j і NLP у цих тестах не викликаються — адреси свідомо неіснуючі
        registry.add("neo4j.uri", () -> "bolt://localhost:7999");
        registry.add("neo4j.password", () -> "unused");
        registry.add("nlp-service.base-url", () -> "http://localhost:8999");
        registry.add("spring.jpa.show-sql", () -> "false");
        // Відповіді в UTF-8, щоб кирилиця в повідомленнях про помилки порівнювалась коректно
        registry.add("server.servlet.encoding.force-response", () -> "true");
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPrivateRepository userPrivateRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /** Тестовий акаунт: id, логін і готовий заголовок Authorization. */
    protected record TestUser(long id, String username, String token) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    /** Реєструє нового користувача з унікальним логіном і паролем PASSWORD, видає йому JWT. */
    protected TestUser createUser(UserRole role) {
        String username = "it_" + UUID.randomUUID().toString().substring(0, 8);
        User user = userService.registerUser(username, username + "@test.local", PASSWORD);
        if (role != UserRole.user) {
            changePrivate(user.getUserId(), p -> p.setRole(role));
        }
        return new TestUser(user.getUserId(), username, jwtTokenProvider.generateToken(user.getUserId(), username, 0));
    }

    /** Змінює приватні дані напряму в БД — так, ніби це зробили з іншого місця (інший адмін, SQL). */
    protected void changePrivate(long userId, Consumer<UserPrivate> change) {
        User user = userRepository.findById(userId).orElseThrow();
        UserPrivate p = userPrivateRepository.findByUser(user).orElseThrow();
        change.accept(p);
        userPrivateRepository.save(p);
    }
}