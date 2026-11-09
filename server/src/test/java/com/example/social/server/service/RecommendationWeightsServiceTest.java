package com.example.social.server.service;

import com.example.social.server.entity.AuditAction;
import com.example.social.server.entity.RecommendationWeightsVersion;
import com.example.social.server.entity.User;
import com.example.social.server.exception.BadRequestException;
import com.example.social.server.repository.RecommendationWeightsRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.server.service.RecommendationService.Weights;
import com.example.social.shared.dto.RecommendationWeightsDto;
import com.example.social.shared.dto.WeightsUpdateRequestDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ваги стрічки: значення за замовчуванням, перевірка суми, кеш після коміту, запис у журнал. */
class RecommendationWeightsServiceTest {

    private static final Weights YML = new Weights(0.10, 0.35, 0.35, 0.10, 0.10, 0.3, 0.7);
    private static final long ADMIN_ID = 83L;

    private final RecommendationWeightsRepository repository = mock(RecommendationWeightsRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private RecommendationWeightsService service;

    @BeforeEach
    void setUp() {
        when(repository.findTopByOrderByWeightsIdDesc()).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.getReferenceById(anyLong())).thenReturn(new User());
        service = newService();

        // Без Spring немає транзакції — вмикаємо реєстрацію afterCommit вручну
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    private RecommendationWeightsService newService() {
        RecommendationWeightsService s = new RecommendationWeightsService(repository, userRepository, auditService,
                YML.popularity(), YML.itemCf(), YML.graphSim(), YML.semantic(), YML.recency(), YML.followedBonus(),
                YML.mmrLambda());
        return s;
    }

    /** Імітує успішний коміт: викликає afterCommit усіх зареєстрованих синхронізацій. */
    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static WeightsUpdateRequestDto request(double p, double i, double g, double s, double r) {
        return new WeightsUpdateRequestDto(p, i, g, s, r, 0.3, 0.7, "тест");
    }

    private static WeightsUpdateRequestDto requestWithLambda(Double lambda) {
        return new WeightsUpdateRequestDto(0.10, 0.35, 0.35, 0.10, 0.10, 0.3, lambda, "тест");
    }

    @Test
    void withoutVersionsInDatabaseUsesApplicationYml() {
        assertEquals(YML, service.current());

        RecommendationWeightsDto dto = service.currentDto();
        assertNull(dto.id());
        assertEquals("Значення з application.yml", dto.comment());
    }

    @Test
    void latestVersionFromDatabaseWinsOverYml() {
        when(repository.findTopByOrderByWeightsIdDesc()).thenReturn(Optional.of(
                new RecommendationWeightsVersion(0.10, 0.30, 0.35, 0.15, 0.10, 0.3, 0.5, null, null)));

        assertEquals(new Weights(0.10, 0.30, 0.35, 0.15, 0.10, 0.3, 0.5), newService().current());
    }

    @Test
    void rejectsWeightsThatDoNotSumToOne() {
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.update(request(0.10, 0.30, 0.35, 0.30, 0.10), ADMIN_ID));

        assertTrue(e.getMessage().contains("1.150"), e.getMessage());
        verify(repository, never()).save(any());
        verify(auditService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void newWeightsApplyOnlyAfterCommit() {
        service.update(request(0.10, 0.30, 0.35, 0.15, 0.10), ADMIN_ID);

        verify(repository).save(any(RecommendationWeightsVersion.class));
        assertEquals(YML, service.current(), "до коміту стрічка працює на старих вагах");

        commit();
        assertEquals(new Weights(0.10, 0.30, 0.35, 0.15, 0.10, 0.3, 0.7), service.current());
    }

    @Test
    void auditDescribesOnlyChangedWeights() {
        service.update(request(0.10, 0.30, 0.35, 0.15, 0.10), ADMIN_ID);

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditService).log(eq(ADMIN_ID), eq(AuditAction.WEIGHTS_UPDATE), eq("WEIGHTS"), any(), details.capture());
        assertEquals("itemCf 0.350 → 0.300; semantic 0.100 → 0.150", details.getValue());
    }

    @Test
    void savingSameValuesIsRecordedAsNoChange() {
        service.update(request(0.10, 0.35, 0.35, 0.10, 0.10), ADMIN_ID);

        verify(auditService).log(eq(ADMIN_ID), eq(AuditAction.WEIGHTS_UPDATE), eq("WEIGHTS"), any(), eq("без змін"));
    }

    // ===== λ для MMR =====

    @Test
    void lambdaChangeIsVersionedAndAudited() {
        service.update(requestWithLambda(0.5), ADMIN_ID);

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditService).log(eq(ADMIN_ID), eq(AuditAction.WEIGHTS_UPDATE), eq("WEIGHTS"), any(), details.capture());
        assertEquals("mmrLambda 0.700 → 0.500", details.getValue());

        commit();
        assertEquals(0.5, service.current().mmrLambda(), 1e-12);
    }

    @Test
    void missingLambdaKeepsCurrentValue() {
        service.update(requestWithLambda(null), ADMIN_ID);

        ArgumentCaptor<RecommendationWeightsVersion> saved = ArgumentCaptor.forClass(RecommendationWeightsVersion.class);
        verify(repository).save(saved.capture());
        assertEquals(0.7, saved.getValue().getMmrLambda(), 1e-12);
    }

    @Test
    void rejectsLambdaOutsideRange() {
        assertThrows(BadRequestException.class, () -> service.update(requestWithLambda(1.5), ADMIN_ID));
        verify(repository, never()).save(any());
    }
}