package com.example.social.server.service;

import com.example.social.server.entity.Post;
import com.example.social.server.entity.Tag;
import com.example.social.server.entity.User;
import com.example.social.server.repository.PostRepository;
import com.example.social.server.repository.TagRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.shared.dto.PostCreateDto;
import com.example.social.shared.dto.PostResponseDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;

    public PostService(PostRepository postRepository,
                       UserRepository userRepository,
                       TagRepository tagRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.tagRepository = tagRepository;
    }

    @Transactional
    public PostResponseDto createPost(PostCreateDto dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + dto.getUserId()));

        Post post = new Post();
        post.setUser(user);
        post.setLabel(dto.getLabel());
        post.setText(dto.getText());
        post.setMediaUrl(dto.getMediaUrl());

        if (dto.getTags() != null && !dto.getTags().isEmpty()) {
            post.setTags(resolveTags(dto.getTags()));
        }

        Post saved = postRepository.save(post);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public PostResponseDto getPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        return toDto(post);
    }

    @Transactional(readOnly = true)
    public List<PostResponseDto> getPostsByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return postRepository.findByUserOrderByCreatedDateDesc(user)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deletePost(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new IllegalArgumentException("Post not found: " + postId);
        }
        postRepository.deleteById(postId);
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

    private PostResponseDto toDto(Post post) {
        List<String> tagNames = post.getTags().stream()
                .map(Tag::getName)
                .collect(Collectors.toList());

        return new PostResponseDto(
                post.getPostId(),
                post.getUser().getUsername(),
                post.getLabel(),
                post.getText(),
                post.getMediaUrl(),
                post.getCreatedDate(),
                tagNames
        );
    }
}