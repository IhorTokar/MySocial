package com.example.social.shared.dto;

/** Користувач і номер його спільноти. */
public record CommunityMemberDto(long userId, String username, long community) {}