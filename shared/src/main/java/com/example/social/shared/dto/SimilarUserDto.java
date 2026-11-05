package com.example.social.shared.dto;

/** Користувач, схожий на заданого, з косинусною подібністю векторів node2vec. */
public record SimilarUserDto(long userId, String username, double similarityScore) {}