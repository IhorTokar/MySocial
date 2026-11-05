package com.example.social.shared.dto;

import java.util.List;

/** Кількість спільнот і розмір кожної. */
public record CommunitySummaryDto(int totalCommunities, List<CommunitySize> communities) {

    public record CommunitySize(long community, long size) {}
}