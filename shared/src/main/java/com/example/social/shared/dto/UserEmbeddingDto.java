package com.example.social.shared.dto;

import java.util.List;

/** Вектор node2vec користувача. */
public record UserEmbeddingDto(long userId, String username, List<Double> embedding) {}