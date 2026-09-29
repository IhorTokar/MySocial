package com.example.social.server.service;

import com.example.social.server.entity.Followers;
import com.example.social.server.entity.Post;
import com.example.social.server.entity.Tag;
import com.example.social.server.entity.User;
import com.example.social.server.repository.CommentRepository;
import com.example.social.server.repository.FollowersRepository;
import com.example.social.server.repository.PostEmbeddingRepository;
import com.example.social.server.repository.PostLikeRepository;
import com.example.social.server.repository.PostRepository;
import com.example.social.server.repository.SavedPostRepository;
import com.example.social.server.repository.TagRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.shared.dto.PostCreateDto;
import com.example.social.shared.dto.PostResponseDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final FollowersRepository followersRepository;
    private final EmbeddingApiService embeddingApiService;
    private final PostEmbeddingRepository postEmbeddingRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentRepository commentRepository;
    private final SavedPostRepository savedPostRepository;

    public PostService(PostRepository postRepository,
                       UserRepository userRepository,
                       TagRepository tagRepository,
                       FollowersRepository followersRepository,
                       EmbeddingApiService embeddingApiService,
                       PostEmbeddingRepository postEmbeddingRepository,
                       PostLikeRepository postLikeRepository,
                       CommentRepository commentRepository,
                       SavedPostRepository savedPostRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.tagRepository = tagRepository;
        this.followersRepository = followersRepository;
        this.embeddingApiService = embeddingApiService;
        this.postEmbeddingRepository = postEmbeddingRepository;
        this.postLikeRepository = postLikeRepository;
        this.commentRepository = commentRepository;
        this.savedPostRepository = savedPostRepository;
    }

    @Transactional
    public PostResponseDto createPost(Long userId, PostCreateDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Post post = new Post();
        post.setUser(user);
        post.setLabel(dto.getLabel());
        post.setText(dto.getText());
        post.setMediaUrl(dto.getMediaUrl());

        if (dto.getTags() != null && !dto.getTags().isEmpty()) {
            post.setTags(resolveTags(dto.getTags()));
        }

        Post saved = postRepository.save(post);

        List<Double> vector = embeddingApiService.embed(dto.getText());
        if (vector != null) {
            postEmbeddingRepository.saveEmbedding(saved.getPostId(), vector);
        }

        return toDtos(List.of(saved), userId).get(0);
    }

    @Transactional(readOnly = true)
    public PostResponseDto getPost(Long postId, Long currentUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        return toDtos(List.of(post), currentUserId).get(0);
    }

    @Transactional
    public PostResponseDto updatePostMedia(Long postId, String mediaUrl) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        post.setMediaUrl(mediaUrl);
        Post saved = postRepository.save(post);
        // медіа може змінювати лише власник, тож дивимось на пост його очима
        return toDtos(List.of(saved), saved.getUser().getUserId()).get(0);
    }

    /** Старий фід «лише підписки» (для порівняння з гібридним). */
    @Transactional(readOnly = true)
    public List<PostResponseDto> getFeed(Long currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + currentUserId));

        List<User> followedUsers = followersRepository.findByFollower(currentUser).stream()
                .map(Followers::getFollowing)
                .collect(Collectors.toList());

        if (followedUsers.isEmpty()) {
            return List.of();
        }
        return toDtos(postRepository.findByUserInOrderByCreatedDateDesc(followedUsers), currentUserId);
    }

    @Transactional(readOnly = true)
    public List<PostResponseDto> getPostsByUser(Long userId, Long currentUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return toDtos(postRepository.findByUserOrderByCreatedDateDesc(user), currentUserId);
    }

    @Transactional
    public void deletePost(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new IllegalArgumentException("Post not found: " + postId);
        }
        postRepository.deleteById(postId);
    }

    @Transactional(readOnly = true)
    public boolean isOwner(Long postId, Long userId) {
        return postRepository.findById(postId)
                .map(post -> post.getUser().getUserId().equals(userId))
                .orElse(false);
    }

    /**
     * Перетворює список постів на DTO разом із лічильниками та статусами поточного користувача.
     * Усе рахується чотирма пакетними запитами на весь список, а не окремо на кожен пост.
     */
    @Transactional(readOnly = true)
    public List<PostResponseDto> toDtos(List<Post> posts, Long currentUserId) {
        if (posts.isEmpty()) {
            return List.of();
        }

        List<Long> ids = posts.stream().map(Post::getPostId).collect(Collectors.toList());

        Map<Long, Long> likeCounts = toCountMap(postLikeRepository.countByPostIds(ids));
        Map<Long, Long> commentCounts = toCountMap(commentRepository.countByPostIds(ids));
        Set<Long> likedIds = currentUserId == null ? Set.of()
                : new HashSet<>(postLikeRepository.findLikedPostIds(currentUserId, ids));
        Set<Long> savedIds = currentUserId == null ? Set.of()
                : new HashSet<>(savedPostRepository.findSavedPostIds(currentUserId, ids));

        return posts.stream().map(post -> {
            User author = post.getUser();
            Long id = post.getPostId();
            List<String> tagNames = post.getTags().stream()
                    .map(Tag::getName)
                    .sorted()
                    .collect(Collectors.toList());

            return new PostResponseDto(
                    id,
                    author.getUserId(),
                    author.getUsername(),
                    author.getDisplayName(),
                    author.getUserAvatarUrl(),
                    post.getLabel(),
                    post.getText(),
                    post.getMediaUrl(),
                    post.getCreatedDate(),
                    tagNames,
                    likeCounts.getOrDefault(id, 0L),
                    commentCounts.getOrDefault(id, 0L),
                    likedIds.contains(id),
                    savedIds.contains(id)
            );
        }).collect(Collectors.toList());
    }

    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }

    private Set<Tag> resolveTags(List<String> tagNames) {
        Set<Tag> tags = new HashSet<>();
        for (String name : tagNames) {
            Tag tag = tagRepository.findByName(name)
                    .orElseGet(() -> {
                        Tag newTag = new Tag();
                        newTag.setName(name);
                        return tagRepository.save(newTag);
                    });
            tags.add(tag);
        }
        return tags;
    }

    @Transactional
    public PostResponseDto updatePost(Long postId, Long currentUserId, PostCreateDto dto) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

        if (!post.getUser().getUserId().equals(currentUserId)) {
            throw new SecurityException("You can only edit your own posts");
        }

        if (dto.getLabel() != null) {
            post.setLabel(dto.getLabel());
        }
        if (dto.getText() != null && !dto.getText().isBlank()) {
            post.setText(dto.getText());
        }
        if (dto.getTags() != null) {
            post.setTags(resolveTags(dto.getTags()));
        }
        post.setUpdateDate(java.time.LocalDateTime.now());

        Post saved = postRepository.save(post);

        // текст міг змінитись — переоцінюємо семантичний вектор для рекомендацій
        List<Double> vector = embeddingApiService.embed(saved.getText());
        if (vector != null) {
            postEmbeddingRepository.saveEmbedding(saved.getPostId(), vector);
        }

        return toDtos(List.of(saved), currentUserId).get(0);
    }
}