package com.example.social.server.controller;

import com.example.social.server.exception.ForbiddenException;
import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.FileStorageService;
import com.example.social.server.service.PostService;
import com.example.social.server.service.RecommendationService;
import com.example.social.server.service.TagSuggestionService;
import com.example.social.shared.dto.PostCreateDto;
import com.example.social.server.exception.BadRequestException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;
    private final RecommendationService recommendationService;
    private final FileStorageService fileStorageService;
    private final TagSuggestionService tagSuggestionService;

    public PostController(PostService postService,
                          RecommendationService recommendationService,
                          FileStorageService fileStorageService,
                          TagSuggestionService tagSuggestionService) {
        this.postService = postService;
        this.recommendationService = recommendationService;
        this.fileStorageService = fileStorageService;
        this.tagSuggestionService = tagSuggestionService;
    }

    /** Текст чернетки поста і теги, які автор уже вказав (для підказок тегів). */
    public record TagSuggestionRequest(String text, List<String> tags) {
    }

    @PostMapping
    public ResponseEntity<?> createPost(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                        @Valid @RequestBody PostCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(postService.createPost(currentUser.getUserId(), dto));
    }

    @PostMapping("/{postId}/media")
    public ResponseEntity<?> uploadMedia(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                         @PathVariable("postId") Long postId,
                                         @RequestParam("file") MultipartFile file) {
        if (!postService.isOwner(postId, currentUser.getUserId())) {
            throw new ForbiddenException("You can only edit your own posts");
        }

        // тип файлу перевіряється за вмістом у storeImage — Content-Type від клієнта не враховується
        String mediaUrl = fileStorageService.storeImage(file);
        return ResponseEntity.ok(postService.updatePostMedia(postId, mediaUrl));
    }

    @GetMapping("/feed/debug")
    public ResponseEntity<?> getFeedDebug(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                          @RequestParam(name = "limit", defaultValue = "30") int limit,
                                          @RequestParam(name = "mmrLambda", required = false) Double mmrLambda) {
        if (mmrLambda != null && (mmrLambda < 0 || mmrLambda > 1)) {
            throw new BadRequestException("mmrLambda має бути в межах [0, 1]");
        }
        return ResponseEntity.ok(recommendationService.getHybridFeedDebug(currentUser.getUserId(), limit, mmrLambda));
    }

    /** Стрічка посторінково: offset — скільки постів клієнт уже показав. */
    @GetMapping("/feed")
    public ResponseEntity<?> getFeed(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                     @RequestParam(name = "limit", defaultValue = "30") int limit,
                                     @RequestParam(name = "offset", defaultValue = "0") int offset) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new BadRequestException("limit має бути від 1 до 100, offset — не менше 0");
        }
        return ResponseEntity.ok(recommendationService.getHybridFeed(currentUser.getUserId(), limit, offset));
    }

    /** Вкладка «Відстежувані»: пости підписок від найновіших, без рекомендацій. */
    @GetMapping("/feed/following")
    public ResponseEntity<?> getFollowingFeed(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                              @RequestParam(name = "limit", defaultValue = "30") int limit,
                                              @RequestParam(name = "offset", defaultValue = "0") int offset) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new BadRequestException("limit має бути від 1 до 100, offset — не менше 0");
        }
        return ResponseEntity.ok(postService.getFollowingFeed(currentUser.getUserId(), limit, offset));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<?> deletePost(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                        @PathVariable("postId") Long postId) {
        if (!postService.isOwner(postId, currentUser.getUserId())) {
            throw new ForbiddenException("You can only delete your own posts");
        }
        postService.deletePost(postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{postId}")
    public ResponseEntity<?> getPost(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                     @PathVariable("postId") Long postId) {
        return ResponseEntity.ok(postService.getPost(postId, currentUser.getUserId()));
    }

    /** «Схожі пости»: найближчі за змістом до цього поста (вектори тексту, pgvector). */
    @GetMapping("/{postId}/similar")
    public ResponseEntity<?> getSimilarPosts(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                             @PathVariable("postId") Long postId,
                                             @RequestParam(name = "limit", defaultValue = "5") int limit) {
        if (limit < 1 || limit > 20) {
            throw new BadRequestException("limit має бути від 1 до 20");
        }
        return ResponseEntity.ok(postService.getSimilarPosts(postId, currentUser.getUserId(), limit));
    }

    /** Підказки тегів для чернетки поста за її текстом. */
    @PostMapping("/tag-suggestions")
    public ResponseEntity<?> suggestTags(@RequestBody TagSuggestionRequest request) {
        return ResponseEntity.ok(tagSuggestionService.suggest(request.text(),
                request.tags() == null ? List.of() : request.tags()));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getPostsByUser(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                            @PathVariable("userId") Long userId) {
        return ResponseEntity.ok(postService.getPostsByUser(userId, currentUser.getUserId()));
    }

    @PatchMapping("/{postId}")
    public ResponseEntity<?> updatePost(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                        @PathVariable("postId") Long postId,
                                        @Valid @RequestBody PostCreateDto dto) {
        return ResponseEntity.ok(postService.updatePost(postId, currentUser.getUserId(), dto));
    }
}