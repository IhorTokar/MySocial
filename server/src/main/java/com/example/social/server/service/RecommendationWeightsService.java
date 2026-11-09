package com.example.social.server.service;

import com.example.social.server.entity.AuditAction;
import com.example.social.server.entity.RecommendationWeightsVersion;
import com.example.social.server.entity.User;
import com.example.social.server.exception.BadRequestException;
import com.example.social.server.exception.NotFoundException;
import com.example.social.server.repository.RecommendationWeightsRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.server.service.RecommendationService.Weights;
import com.example.social.shared.dto.RecommendationWeightsDto;
import com.example.social.shared.dto.WeightsUpdateRequestDto;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Чинні ваги гібридної стрічки. Зберігаються в БД з історією змін;
 * значення з application.yml використовуються, лише поки в БД немає жодної версії.
 */
@Service
public class RecommendationWeightsService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationWeightsService.class);
    private static final double SUM_TOLERANCE = 1e-3;

    private final RecommendationWeightsRepository weightsRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final Weights configDefaults;

    /** Кеш чинних ваг: стрічка читає їх при кожному запиті, тому без звернення до БД. */
    private volatile Weights current;

    public RecommendationWeightsService(RecommendationWeightsRepository weightsRepository,
                                        UserRepository userRepository,
                                        AuditService auditService,
                                        @Value("${recommendation.weights.popularity:0.20}") double popularity,
                                        @Value("${recommendation.weights.item-cf:0.25}") double itemCf,
                                        @Value("${recommendation.weights.graph-sim:0.20}") double graphSim,
                                        @Value("${recommendation.weights.semantic:0.20}") double semantic,
                                        @Value("${recommendation.weights.recency:0.15}") double recency,
                                        @Value("${recommendation.weights.followed-bonus:0.3}") double followedBonus,
                                        @Value("${recommendation.mmr.lambda:1.0}") double mmrLambda) {
        this.weightsRepository = weightsRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.configDefaults = new Weights(popularity, itemCf, graphSim, semantic, recency, followedBonus, mmrLambda);
        if (popularity < 0 || itemCf < 0 || graphSim < 0 || semantic < 0 || recency < 0 || followedBonus < 0) {
            throw new IllegalStateException("recommendation.weights.*: значення не можуть бути від'ємними");
        }
        if (mmrLambda < 0 || mmrLambda > 1) {
            throw new IllegalStateException("recommendation.mmr.lambda має бути в межах [0, 1]");
        }
    }

    @PostConstruct
    void loadFromDatabase() {
        current = weightsRepository.findTopByOrderByWeightsIdDesc()
                .map(RecommendationWeightsService::toWeights)
                .orElse(configDefaults);
        log.info("Вагові коефіцієнти гібридної стрічки: {}", current);
    }

    /** Ваги, з якими зараз рахується стрічка. */
    public Weights current() {
        return current;
    }

    @Transactional(readOnly = true)
    public RecommendationWeightsDto currentDto() {
        return weightsRepository.findTopByOrderByWeightsIdDesc()
                .map(RecommendationWeightsService::toDto)
                .orElseGet(() -> new RecommendationWeightsDto(null,
                        configDefaults.popularity(), configDefaults.itemCf(), configDefaults.graphSim(),
                        configDefaults.semantic(), configDefaults.recency(), configDefaults.followedBonus(),
                        configDefaults.mmrLambda(), "Значення з application.yml", null, null));
    }

    @Transactional(readOnly = true)
    public List<RecommendationWeightsDto> history(int limit) {
        return weightsRepository.findAllByOrderByWeightsIdDesc(PageRequest.of(0, limit)).stream()
                .map(RecommendationWeightsService::toDto)
                .toList();
    }

    @Transactional
    public RecommendationWeightsDto update(WeightsUpdateRequestDto request, Long adminId) {
        // λ не передали (старий клієнт, Postman) — лишається чинне значення
        double mmrLambda = request.mmrLambda() != null ? request.mmrLambda() : current.mmrLambda();
        Weights next = new Weights(request.popularity(), request.itemCf(), request.graphSim(),
                request.semantic(), request.recency(), request.followedBonus(), mmrLambda);
        validate(next);
        return saveVersion(next, request.comment(), adminId, AuditAction.WEIGHTS_UPDATE);
    }

    /** Повертає ваги з версії versionId. Історія не переписується: додається нова версія з тими ж значеннями. */
    @Transactional
    public RecommendationWeightsDto restore(long versionId, Long adminId) {
        RecommendationWeightsVersion old = weightsRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Версію ваг не знайдено: " + versionId));
        return saveVersion(toWeights(old), "Відновлено версію #" + versionId, adminId, AuditAction.WEIGHTS_RESTORE);
    }

    private RecommendationWeightsDto saveVersion(Weights next, String comment, Long adminId, AuditAction action) {
        Weights before = current;
        User admin = adminId == null ? null : userRepository.getReferenceById(adminId);

        RecommendationWeightsVersion saved = weightsRepository.save(new RecommendationWeightsVersion(
                next.popularity(), next.itemCf(), next.graphSim(), next.semantic(), next.recency(),
                next.followedBonus(), next.mmrLambda(), comment, admin));

        auditService.log(adminId, action, "WEIGHTS", saved.getWeightsId(), describeChange(before, next));

        // Кеш оновлюється лише після успішного коміту: якщо транзакція відкотиться, стрічка лишиться на старих вагах
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                current = next;
                log.info("Ваги стрічки змінено: {}", next);
            }
        });
        return toDto(saved);
    }

    private static void validate(Weights w) {
        double sum = w.popularity() + w.itemCf() + w.graphSim() + w.semantic() + w.recency();
        if (Math.abs(sum - 1.0) > SUM_TOLERANCE) {
            throw new BadRequestException(String.format(Locale.ROOT,
                    "Сума ваг popularity, itemCf, graphSim, semantic, recency має дорівнювати 1 (зараз %.3f)", sum));
        }
        if (w.mmrLambda() < 0 || w.mmrLambda() > 1) {
            throw new BadRequestException("mmrLambda має бути в межах [0, 1]");
        }
    }

    private static String describeChange(Weights a, Weights b) {
        List<String> parts = new ArrayList<>();
        diff(parts, "popularity", a.popularity(), b.popularity());
        diff(parts, "itemCf", a.itemCf(), b.itemCf());
        diff(parts, "graphSim", a.graphSim(), b.graphSim());
        diff(parts, "semantic", a.semantic(), b.semantic());
        diff(parts, "recency", a.recency(), b.recency());
        diff(parts, "followedBonus", a.followedBonus(), b.followedBonus());
        diff(parts, "mmrLambda", a.mmrLambda(), b.mmrLambda());
        return parts.isEmpty() ? "без змін" : String.join("; ", parts);
    }

    private static void diff(List<String> parts, String name, double before, double after) {
        if (Math.abs(before - after) > 1e-9) {
            parts.add(String.format(Locale.ROOT, "%s %.3f → %.3f", name, before, after));
        }
    }

    private static Weights toWeights(RecommendationWeightsVersion v) {
        return new Weights(v.getPopularity(), v.getItemCf(), v.getGraphSim(),
                v.getSemantic(), v.getRecency(), v.getFollowedBonus(), v.getMmrLambda());
    }

    private static RecommendationWeightsDto toDto(RecommendationWeightsVersion v) {
        return new RecommendationWeightsDto(v.getWeightsId(),
                v.getPopularity(), v.getItemCf(), v.getGraphSim(), v.getSemantic(), v.getRecency(),
                v.getFollowedBonus(), v.getMmrLambda(), v.getComment(),
                v.getCreatedBy() == null ? null : v.getCreatedBy().getUsername(),
                v.getCreatedAt());
    }
}