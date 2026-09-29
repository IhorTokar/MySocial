package com.example.social.server.service;

import com.example.social.server.entity.Post;
import com.example.social.server.entity.User;
import com.example.social.server.repository.FollowersRepository;
import com.example.social.server.repository.PostRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.shared.dto.SearchResultDto;
import com.example.social.shared.dto.UserSummaryDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int USER_LIMIT = 20;
    private static final int POST_LIMIT = 30;

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final FollowersRepository followersRepository;
    private final PostService postService;

    public SearchService(UserRepository userRepository,
                         PostRepository postRepository,
                         FollowersRepository followersRepository,
                         PostService postService) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.followersRepository = followersRepository;
        this.postService = postService;
    }

    @Transactional(readOnly = true)
    public SearchResultDto search(String rawQuery, Long currentUserId) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < MIN_QUERY_LENGTH) {
            return new SearchResultDto(List.of(), List.of());
        }

        boolean isTagSearch = query.startsWith("#");
        String normalizedQuery = isTagSearch ? query.substring(1).trim() : query;

        if (normalizedQuery.length() < MIN_QUERY_LENGTH) {
            return new SearchResultDto(List.of(), List.of());
        }

        List<User> foundUsers = isTagSearch
                ? List.of() // пошук за тегом стосується лише постів, юзерів не шукаємо
                : userRepository.findTop20ByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                normalizedQuery, normalizedQuery);

        User currentUser = currentUserId != null ? userRepository.findById(currentUserId).orElse(null) : null;

        List<UserSummaryDto> userDtos = foundUsers.stream()
                .limit(USER_LIMIT)
                .map(u -> new UserSummaryDto(
                        u.getUserId(),
                        u.getUsername(),
                        u.getDisplayName(),
                        u.getUserAvatarUrl(),
                        currentUser != null && !currentUser.getUserId().equals(u.getUserId())
                                && followersRepository.existsByFollowerAndFollowing(currentUser, u)
                ))
                .collect(Collectors.toList());

        List<Post> foundPosts = isTagSearch
                ? postRepository.searchPostsByTag(normalizedQuery, PageRequest.of(0, POST_LIMIT))
                : postRepository.searchPostsByText(normalizedQuery, PageRequest.of(0, POST_LIMIT));

        List<com.example.social.shared.dto.PostResponseDto> postDtos = postService.toDtos(foundPosts, currentUserId);

        return new SearchResultDto(userDtos, postDtos);
    }
}