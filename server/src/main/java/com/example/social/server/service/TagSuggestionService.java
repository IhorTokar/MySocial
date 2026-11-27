package com.example.social.server.service;

import com.example.social.server.repository.PostEmbeddingRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Підказки тегів за текстом поста: текст перетворюється на вектор, знаходяться найближчі за змістом
 * пости, і їхні теги «голосують» ({@link TagSuggestions}). Автор тег не зобов'язаний приймати —
 * це лише підказка, яка зменшує кількість постів без тегів або з випадковими тегами.
 */
@Service
public class TagSuggestionService {

    /** Коротший текст не несе досить змісту для підказки. */
    static final int MIN_TEXT_LENGTH = 15;
    private static final int NEIGHBOURS = 25;
    private static final double MIN_SIMILARITY = 0.3;
    private static final double MIN_SHARE = 0.15;
    private static final int MAX_SUGGESTIONS = 5;

    private final EmbeddingApiService embeddingApiService;
    private final PostEmbeddingRepository postEmbeddingRepository;

    public TagSuggestionService(EmbeddingApiService embeddingApiService,
                                PostEmbeddingRepository postEmbeddingRepository) {
        this.embeddingApiService = embeddingApiService;
        this.postEmbeddingRepository = postEmbeddingRepository;
    }

    /**
     * @param existingTags теги, що вже є в полі (їх не пропонуємо)
     * @return до п'яти тегів; порожній список, якщо текст короткий, NLP-сервіс недоступний
     *         або схожих постів немає
     */
    public List<TagSuggestions.Suggestion> suggest(String text, Collection<String> existingTags) {
        if (text == null || text.trim().length() < MIN_TEXT_LENGTH) {
            return List.of();
        }
        List<Double> vector = embeddingApiService.embed(text.trim());
        if (vector == null) {
            return List.of();
        }
        List<PostEmbeddingRepository.Neighbour> nearest =
                postEmbeddingRepository.findNearest(vector, NEIGHBOURS, MIN_SIMILARITY);
        Map<Long, List<String>> tags = postEmbeddingRepository.findTagsOf(
                nearest.stream().map(PostEmbeddingRepository.Neighbour::postId).toList());
        List<TagSuggestions.Neighbour> votes = new ArrayList<>();
        for (PostEmbeddingRepository.Neighbour n : nearest) {
            votes.add(new TagSuggestions.Neighbour(n.similarity(), tags.getOrDefault(n.postId(), List.of())));
        }
        return TagSuggestions.rank(votes, MAX_SUGGESTIONS, MIN_SHARE, existingTags);
    }
}
