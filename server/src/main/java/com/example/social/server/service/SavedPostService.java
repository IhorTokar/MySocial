package com.example.social.server.service;

import com.example.social.server.entity.Post;
import com.example.social.server.entity.SavedPost;
import com.example.social.server.entity.User;
import com.example.social.server.repository.PostRepository;
import com.example.social.server.repository.SavedPostRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.shared.dto.PostResponseDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SavedPostService {

    private final SavedPostRepository savedPostRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostService postService;

    public SavedPostService(SavedPostRepository savedPostRepository,
                            PostRepository postRepository,
                            UserRepository userRepository,
                            PostService postService) {
        this.savedPostRepository = savedPostRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.postService = postService;
    }

    @Transactional
    public void save(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (savedPostRepository.existsByUserAndPost(user, post)) {
            throw new IllegalArgumentException("Post already saved");
        }

        SavedPost savedPost = new SavedPost();
        savedPost.setUser(user);
        savedPost.setPost(post);
        savedPostRepository.save(savedPost);
    }

    @Transactional(readOnly = true)
    public boolean isSavedByUser(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return savedPostRepository.existsByUserAndPost(user, post);
    }

    @Transactional
    public void unsave(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        SavedPost savedPost = savedPostRepository.findByUserAndPost(user, post)
                .orElseThrow(() -> new IllegalArgumentException("Post is not saved"));

        savedPostRepository.delete(savedPost);
    }

    @Transactional(readOnly = true)
    public List<PostResponseDto> getSavedPosts(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return savedPostRepository.findByUser(user).stream()
                .map(sp -> postService.getPost(sp.getPost().getPostId()))
                .collect(Collectors.toList());
    }
}