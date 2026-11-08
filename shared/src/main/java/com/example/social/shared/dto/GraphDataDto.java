package com.example.social.shared.dto;

import java.util.List;

/**
 * Граф підписок для перегляду в адмін-панелі.
 * community == null — спільноту для користувача ще не визначено (алгоритм не запускався після появи вузла).
 */
public record GraphDataDto(String algorithm, List<Node> nodes, List<Edge> edges) {

    public record Node(long userId, String username, Long community) {
    }

    /** Ребро FOLLOWS: source підписаний на target. */
    public record Edge(long source, long target) {
    }
}