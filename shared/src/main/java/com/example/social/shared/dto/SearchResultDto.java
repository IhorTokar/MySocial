package com.example.social.shared.dto;

import java.util.List;

public class SearchResultDto {

    private List<UserSummaryDto> users;
    private List<PostResponseDto> posts;

    public SearchResultDto(List<UserSummaryDto> users, List<PostResponseDto> posts) {
        this.users = users;
        this.posts = posts;
    }

    public List<UserSummaryDto> getUsers() { return users; }
    public List<PostResponseDto> getPosts() { return posts; }
}