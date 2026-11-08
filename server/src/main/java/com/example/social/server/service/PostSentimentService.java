package com.example.social.server.service;

import com.example.social.server.entity.Post;
import com.example.social.server.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Тональність постів, збережена в БД (posts.sentiment).
 * Рахується один раз — при створенні або зміні тексту; аналіз спільнот лише читає готові значення.
 */
@Service
public class PostSentimentService {

    private static final Logger log = LoggerFactory.getLogger(PostSentimentService.class);

    private final PostRepository postRepository;
    private final SentimentService sentimentService;

    public PostSentimentService(PostRepository postRepository, SentimentService sentimentService) {
        this.postRepository = postRepository;
        this.sentimentService = sentimentService;
    }

    /** Обчислює тональність поточного тексту і записує в сутність (зберігає транзакція того, хто викликає). */
    public void apply(Post post) {
        SentimentService.Result result = sentimentService.analyzeWithSource(post.getText());
        post.setSentiment(result.value(), result.source());
    }

    /**
     * Дораховує тональність для постів зі списку, у яких її ще немає, і одразу зберігає.
     * Окрема транзакція (REQUIRES_NEW): викликається з read-only аналізу спільнот, а записати треба.
     *
     * @return postId → тональність для щойно обчислених постів
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<Long, Double> fillMissing(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Double> computed = new HashMap<>();
        for (Post post : postRepository.findByPostIdInAndSentimentIsNull(postIds)) {
            apply(post);
            computed.put(post.getPostId(), post.getSentiment());
        }
        if (!computed.isEmpty()) {
            log.info("Sentiment computed and stored for {} posts", computed.size());
        }
        return computed;
    }

    /**
     * Заповнює тональність для всіх постів без неї.
     *
     * @param retryLexicon true — також перераховати пости, оцінені словником, коли NLP-сервіс був недоступний
     */
    @Transactional
    public Map<String, Object> backfill(boolean retryLexicon) {
        List<Post> posts = retryLexicon
                ? postRepository.findBySentimentIsNullOrSentimentSource(SentimentService.SOURCE_LEXICON)
                : postRepository.findBySentimentIsNull();

        Map<String, Integer> bySource = new LinkedHashMap<>();
        for (Post post : posts) {
            apply(post);
            bySource.merge(post.getSentimentSource(), 1, Integer::sum);
        }

        log.info("Sentiment backfill: {} posts, by source {}", posts.size(), bySource);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processed", posts.size());
        result.put("bySource", bySource);
        return result;
    }
}