package com.example.social.server.integration;

import com.example.social.server.entity.UserRole;
import com.example.social.server.repository.EchoHistoryRepository;
import com.example.social.server.repository.EchoHistoryRepository.CommunityRow;
import com.example.social.shared.dto.EchoSnapshotDto;
import com.example.social.shared.dto.UserEchoPointDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Таблиці V8 і запити історії ехо-камер на справжній PostgreSQL (масиви bigint[], оператор @>). */
class EchoHistoryRepositoryIntegrationTest extends IntegrationTestBase {

    @Autowired
    private EchoHistoryRepository repository;

    private static CommunityRow row(long community, double echoScore, Double cohesion, Long... members) {
        return new CommunityRow(community, members.length, 3, 0.5, cohesion, 0.1, 0.9, echoScore, List.of(members));
    }

    private long snapshot(String algorithm, Long adminId, CommunityRow... rows) {
        long id = repository.insertSnapshot(algorithm, adminId == null ? "scheduled" : "manual", adminId,
                rows.length, 0, 0.5, 0.9);
        repository.insertCommunities(id, List.of(rows));
        return id;
    }

    @Test
    void snapshotsAreStoredAndPreviousIsSearchedWithinSameAlgorithm() {
        TestUser admin = createUser(UserRole.admin);
        long first = snapshot("louvain", admin.id(), row(5, 0.7, null, 1L, 2L));
        snapshot("leiden", null, row(8, 0.6, 0.8, 1L, 2L));
        long second = snapshot("louvain", null, row(9, 0.8, 0.85, 1L, 2L, 3L));

        EchoSnapshotDto stored = repository.findSnapshot(first).orElseThrow();
        assertEquals("manual", stored.source());
        assertEquals(admin.username(), stored.createdBy());

        // між двома louvain-знімками лежить leiden — його треба пропустити
        assertEquals(first, repository.findPrevious("louvain", second).orElseThrow().snapshotId());

        List<EchoSnapshotDto> history = repository.findSnapshots("louvain", 2);
        assertEquals(List.of(first, second), history.stream().map(EchoSnapshotDto::snapshotId).toList());

        CommunityRow community = repository.findCommunities(first).get(0);
        assertEquals(List.of(1L, 2L), community.memberIds());
        assertNull(community.semanticCohesion());   // null зберігається як null, а не 0
    }

    @Test
    void userTrajectoryFollowsUserAcrossRenumberedCommunities() {
        long u1 = createUser(UserRole.user).id();
        long u2 = createUser(UserRole.user).id();
        long u3 = createUser(UserRole.user).id();

        long first = snapshot("louvain", null, row(5, 0.70, 0.8, u1, u2), row(7, 0.40, 0.6, u3, 999_999L));
        long second = snapshot("louvain", null, row(11, 0.85, 0.9, u1, u2, u3));

        List<UserEchoPointDto> trajectory = repository.findUserTrajectory(u3, "louvain", 10);

        assertEquals(List.of(first, second), trajectory.stream().map(UserEchoPointDto::snapshotId).toList());
        assertEquals(7L, trajectory.get(0).community());
        assertEquals(0.40, trajectory.get(0).echoChamberScore(), 1e-12);
        assertEquals(11L, trajectory.get(1).community());   // номер змінився, а користувач той самий
        assertEquals(3, trajectory.get(1).memberCount());
        assertEquals(0.5, trajectory.get(1).networkEchoScore(), 1e-12);
    }
}