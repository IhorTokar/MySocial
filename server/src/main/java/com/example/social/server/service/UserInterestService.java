package com.example.social.server.service;

import com.example.social.server.exception.BadRequestException;
import com.example.social.server.repository.UserInterestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Холодний старт: людина без вподобань і підписок обирає кілька тем, і стрічка «Для вас» одразу
 * підбирає пости за ними (див. RecommendationService). Теми можна змінити будь-коли на сторінці «Моя стрічка».
 */
@Service
public class UserInterestService {

    static final int MAX_INTERESTS = 10;
    private static final int OPTIONS = 24;

    private final UserInterestRepository repository;

    public UserInterestService(UserInterestRepository repository) {
        this.repository = repository;
    }

    /**
     * selected — обрані теми; options — найпоширеніші теги мережі (з кількістю постів);
     * needsOnboarding — запропонувати вибір тем зараз: людину ще не питали, теми не обрано
     * і власних сигналів для стрічки (вподобань, коментарів, підписок) немає.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> get(long userId) {
        List<String> selected = repository.findTagNames(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selected", selected);
        result.put("options", repository.popularTags(OPTIONS));
        result.put("needsOnboarding", selected.isEmpty() && !repository.wasAsked(userId)
                && !repository.hasActivity(userId));
        result.put("maxInterests", MAX_INTERESTS);
        return result;
    }

    /** Замінює обрані теми (порожній список — «пропустити»); надалі вибір тем сам не з'являється. */
    @Transactional
    public Map<String, Object> update(long userId, List<String> tags) {
        List<String> clean = normalize(tags);
        if (clean.size() > MAX_INTERESTS) {
            throw new BadRequestException("Можна обрати не більше " + MAX_INTERESTS + " тем");
        }
        repository.replace(userId, clean);
        repository.markAsked(userId);
        return get(userId);
    }

    /** Без «#», пробілів на краях і повторів; порядок збережено. */
    static List<String> normalize(List<String> tags) {
        Set<String> result = new LinkedHashSet<>();
        if (tags != null) {
            for (String t : tags) {
                if (t == null) {
                    continue;
                }
                String name = t.trim();
                while (name.startsWith("#")) {
                    name = name.substring(1).trim();
                }
                if (!name.isEmpty()) {
                    result.add(name);
                }
            }
        }
        return new ArrayList<>(result);
    }
}
