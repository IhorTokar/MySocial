package com.example.social.server.controller;

import com.example.social.server.exception.BadRequestException;
import com.example.social.server.service.EmbeddingQualityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Перевірки NLP-частини (лише для адміністратора: /api/graph/**). */
@RestController
public class NlpInsightController {

    private final EmbeddingQualityService embeddingQualityService;

    public NlpInsightController(EmbeddingQualityService embeddingQualityService) {
        this.embeddingQualityService = embeddingQualityService;
    }

    /** Чи найближчі за вектором пости — на ту саму тему (точність@k проти випадкових постів). */
    @GetMapping("/api/graph/nlp/text-vectors")
    public ResponseEntity<Map<String, Object>> textVectors(
            @RequestParam(name = "sample", defaultValue = "300") int sample,
            @RequestParam(name = "k", defaultValue = "10") int k,
            @RequestParam(name = "seed", defaultValue = "7") long seed) {
        if (sample < 10 || sample > 2000 || k < 1 || k > 50) {
            throw new BadRequestException("sample: 10–2000, k: 1–50");
        }
        return ResponseEntity.ok(embeddingQualityService.evaluate(sample, k, seed));
    }
}
