package com.example.social.server.service;

import com.example.social.server.entity.User;
import com.example.social.server.repository.DiscoveryRepository;
import com.example.social.server.repository.FollowersRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.shared.dto.SimilarUserDto;
import com.example.social.shared.dto.SuggestedUserDto;
import com.example.social.shared.dto.TrendingTagDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Бічна панель: гарячі теми та кого почитати.
 * Без @Transactional навмисно: якщо Neo4j недоступний, виняток зі схожості не повинен
 * позначати спільну транзакцію як rollback-only — панель просто обійдеться без цього сигналу.
 */
@Service
public class DiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);

    static final int TREND_DAYS = 7;
    private static final int CANDIDATES_PER_SOURCE = 20;
    private static final LocalDateTime ALL_TIME = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final DiscoveryRepository discoveryRepository;
    private final UserSimilarityService userSimilarityService;
    private final UserRepository userRepository;
    private final FollowersRepository followersRepository;

    public DiscoveryService(DiscoveryRepository discoveryRepository,
                            UserSimilarityService userSimilarityService,
                            UserRepository userRepository,
                            FollowersRepository followersRepository) {
        this.discoveryRepository = discoveryRepository;
        this.userSimilarityService = userSimilarityService;
        this.userRepository = userRepository;
        this.followersRepository = followersRepository;
    }

    /** Найактивніші теги за тиждень; якщо за тиждень тиша — за весь час, щоб панель не була порожньою. */
    public List<TrendingTagDto> trendingTags(int limit) {
        List<TrendingTagDto> recent = discoveryRepository.trendingTags(LocalDateTime.now().minusDays(TREND_DAYS), limit);
        return recent.isEmpty() ? discoveryRepository.trendingTags(ALL_TIME, limit) : recent;
    }

    /**
     * Три джерела, у порядку пріоритету:
     * 1) схожі за графом (node2vec) і 2) кого читають мої підписки — чергуються, щоб було видно обидва сигнали;
     * 3) популярні автори — добирають, якщо перших двох замало (новий користувач).
     * Якщо користувач потрапив у кілька джерел, причини об'єднуються.
     */
    public List<SuggestedUserDto> suggestedUsers(long userId, int limit) {
        Map<Long, List<String>> reasons = new LinkedHashMap<>();

        Iterator<Long> similar = similarUserIds(userId).iterator();
        Iterator<Map.Entry<Long, Long>> viaFollowings =
                discoveryRepository.followedByMyFollowings(userId, CANDIDATES_PER_SOURCE).entrySet().iterator();
        while (similar.hasNext() || viaFollowings.hasNext()) {
            if (similar.hasNext()) {
                addReason(reasons, similar.next(), "Схожі інтереси");
            }
            if (viaFollowings.hasNext()) {
                Map.Entry<Long, Long> e = viaFollowings.next();
                addReason(reasons, e.getKey(), followedByText(e.getValue()));
            }
        }
        for (Long id : discoveryRepository.popularNotFollowed(userId, CANDIDATES_PER_SOURCE).keySet()) {
            reasons.putIfAbsent(id, new ArrayList<>(List.of("Популярний автор")));
        }

        Set<Long> blocked = discoveryRepository.blockedUserIds();
        reasons.keySet().removeIf(id -> id == userId || blocked.contains(id));

        List<Long> ids = reasons.keySet().stream().limit(limit).toList();
        Map<Long, User> users = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));

        List<SuggestedUserDto> result = new ArrayList<>(ids.size());
        for (Long id : ids) {
            User u = users.get(id);
            if (u != null) {
                result.add(new SuggestedUserDto(u.getUserId(), u.getUsername(), u.getDisplayName(),
                        u.getUserAvatarUrl(), followersRepository.countByFollowing(u),
                        String.join(" · ", reasons.get(id))));
            }
        }
        return result;
    }

    private List<Long> similarUserIds(long userId) {
        try {
            return userSimilarityService.getSimilarUsers(userId, CANDIDATES_PER_SOURCE, true).stream()
                    .filter(s -> s.similarityScore() > 0)
                    .map(SimilarUserDto::userId)
                    .toList();
        } catch (RuntimeException e) {
            // Neo4j недоступний або ембедингів ще не пораховано — працюємо без цього сигналу
            log.debug("[discover uid={}] graph similarity skipped: {}", userId, e.getMessage());
            return List.of();
        }
    }

    private static void addReason(Map<Long, List<String>> reasons, long userId, String reason) {
        List<String> list = reasons.computeIfAbsent(userId, k -> new ArrayList<>());
        if (!list.contains(reason)) {
            list.add(reason);
        }
    }

    /** «Читає ваша підписка» / «Читають 3 ваші підписки» / «Читають 5 ваших підписок». */
    static String followedByText(long count) {
        if (count == 1) {
            return "Читає ваша підписка";
        }
        long mod10 = count % 10;
        long mod100 = count % 100;
        String noun;
        if (mod10 == 1 && mod100 != 11) {
            noun = "ваша підписка";
        } else if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            noun = "ваші підписки";
        } else {
            noun = "ваших підписок";
        }
        return "Читають " + count + " " + noun;
    }
}