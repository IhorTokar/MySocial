package com.example.social.server.service;

import com.example.social.server.exception.ConflictException;
import com.example.social.server.repository.PostEmbeddingRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Перевірка якості векторів текстів на постах мережі, що мають теги ({@link EmbeddingQuality}). */
@Service
public class EmbeddingQualityService {

    private final PostEmbeddingRepository repository;

    public EmbeddingQualityService(PostEmbeddingRepository repository) {
        this.repository = repository;
    }

    /**
     * @param sample скільки постів перевірити (випадкова вибірка з фіксованим зерном seed)
     * @param k      скільки найближчих сусідів брати
     */
    public Map<String, Object> evaluate(int sample, int k, long seed) {
        List<Long> ids = repository.findTaggedPostIdsWithEmbedding();
        if (ids.size() < k + 1) {
            throw new ConflictException("Замало постів із вектором і тегом (" + ids.size()
                    + ") — спершу наповніть мережу (seed.py)");
        }
        List<Long> shuffled = new ArrayList<>(ids);
        Collections.shuffle(shuffled, new Random(seed));
        List<Long> chosen = shuffled.subList(0, Math.min(sample, shuffled.size()));

        long started = System.nanoTime();
        Map<Long, List<Long>> neighbours = repository.findNearestForEach(chosen, k);
        double seconds = (System.nanoTime() - started) / 1e9;

        EmbeddingQuality.Result result = EmbeddingQuality.evaluate(chosen, neighbours, repository.findTagsOf(ids),
                repository.findAuthorsOf(ids), k);
        Map<String, Object> report = new LinkedHashMap<>(EmbeddingQuality.toMap(result));
        report.put("seed", seed);
        report.put("searchSeconds", Math.round(seconds * 1000) / 1000.0);
        return report;
    }
}
