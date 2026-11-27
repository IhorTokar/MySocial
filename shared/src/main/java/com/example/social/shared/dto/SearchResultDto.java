package com.example.social.shared.dto;

import java.util.List;

public class SearchResultDto {

    /** Звичайний пошук: збіг слів у тексті / заголовку або точний тег. */
    public static final String MODE_TEXT = "text";
    /** Пошук за змістом: пости, найближчі до запиту за вектором тексту. */
    public static final String MODE_SEMANTIC = "semantic";
    /** Просили пошук за змістом, але NLP-сервіс недоступний — показано звичайний пошук. */
    public static final String MODE_SEMANTIC_UNAVAILABLE = "semantic-unavailable";

    private List<UserSummaryDto> users;
    private List<PostResponseDto> posts;
    private String mode;

    public SearchResultDto(List<UserSummaryDto> users, List<PostResponseDto> posts) {
        this(users, posts, MODE_TEXT);
    }

    public SearchResultDto(List<UserSummaryDto> users, List<PostResponseDto> posts, String mode) {
        this.users = users;
        this.posts = posts;
        this.mode = mode;
    }

    public List<UserSummaryDto> getUsers() { return users; }
    public List<PostResponseDto> getPosts() { return posts; }
    public String getMode() { return mode; }
}
