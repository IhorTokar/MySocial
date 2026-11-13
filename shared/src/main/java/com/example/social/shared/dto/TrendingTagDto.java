package com.example.social.shared.dto;

/**
 * Гаряча тема для бічної панелі.
 *
 * @param posts    нових постів із цим тегом за період
 * @param likes    вподобань постів із цим тегом за період
 * @param comments коментарів до постів із цим тегом за період
 */
public record TrendingTagDto(String tag, long posts, long likes, long comments) {
}