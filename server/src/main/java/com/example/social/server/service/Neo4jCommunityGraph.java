package com.example.social.server.service;

import com.example.social.shared.dto.CommunityMemberDto;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class Neo4jCommunityGraph implements CommunityGraph {

    private final GraphSyncService graphSyncService;
    private final CommunityDetectionService communityDetectionService;

    public Neo4jCommunityGraph(GraphSyncService graphSyncService, CommunityDetectionService communityDetectionService) {
        this.graphSyncService = graphSyncService;
        this.communityDetectionService = communityDetectionService;
    }

    @Override
    public void sync() {
        graphSyncService.syncAll();
    }

    @Override
    public void detect(boolean leiden) {
        if (leiden) {
            communityDetectionService.detectCommunitiesLeiden();
        } else {
            communityDetectionService.detectCommunities();
        }
    }

    @Override
    public Map<Long, Long> labels(boolean leiden) {
        List<CommunityMemberDto> rows = leiden
                ? communityDetectionService.getCommunitiesLeiden()
                : communityDetectionService.getCommunities();
        Map<Long, Long> result = new HashMap<>();
        rows.forEach(row -> result.put(row.userId(), row.community()));
        return result;
    }
}