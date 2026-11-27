package com.example.social.server.service;

import com.example.social.server.entity.Post;
import com.example.social.server.entity.Tag;
import com.example.social.server.repository.UserInterestRepository;
import com.example.social.shared.dto.CommunityAnalysisDto;
import com.example.social.shared.dto.UserEchoPointDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Сторінка «Моя стрічка»: аналіз спільнот, показаний самій людині. Її спільнота (теми, ключові слова,
 * показник ехо-камери), склад її стрічки «Для вас» зараз (скільки постів зі своєї спільноти порівняно
 * з випадковою стрічкою) і що вона вподобує — теми й настрій порівняно з усією мережею.
 */
@Service
public class MyFeedInsightsService {

    private static final Logger log = LoggerFactory.getLogger(MyFeedInsightsService.class);

    /** Скільки перших постів стрічки аналізувати — приблизно один екран прокручування. */
    static final int FEED_SIZE = 20;
    private static final int TOP_TAGS = 6;
    private static final int KEYWORDS = 5;

    private final RecommendationService recommendationService;
    private final RecommendationWeightsService weightsService;
    private final CommunityGraph communityGraph;
    private final CommunityAnalysisService communityAnalysisService;
    private final EchoHistoryService echoHistoryService;
    private final CommunityData communityData;
    private final UserInterestRepository interestRepository;
    private final JdbcTemplate jdbc;

    public MyFeedInsightsService(RecommendationService recommendationService,
                                 RecommendationWeightsService weightsService,
                                 CommunityGraph communityGraph,
                                 CommunityAnalysisService communityAnalysisService,
                                 EchoHistoryService echoHistoryService,
                                 CommunityData communityData,
                                 UserInterestRepository interestRepository,
                                 JdbcTemplate jdbc) {
        this.recommendationService = recommendationService;
        this.weightsService = weightsService;
        this.communityGraph = communityGraph;
        this.communityAnalysisService = communityAnalysisService;
        this.echoHistoryService = echoHistoryService;
        this.communityData = communityData;
        this.interestRepository = interestRepository;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> forUser(long userId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. Спільнота (Louvain). Neo4j може бути недоступний — тоді сторінка без цього розділу.
        Map<Long, Long> labels = Map.of();
        try {
            labels = communityGraph.labels(false);
        } catch (RuntimeException e) {
            log.warn("Спільноти недоступні для «Моєї стрічки»: {}", e.getMessage());
        }
        Long myCommunity = labels.get(userId);
        Set<Long> members = new HashSet<>();
        if (myCommunity != null) {
            labels.forEach((u, c) -> {
                if (c.equals(myCommunity)) {
                    members.add(u);
                }
            });
        }
        result.put("community", communityBlock(userId, myCommunity, members));

        // 2. Стрічка «Для вас» зараз
        List<RecommendationService.FeedItem> feed = recommendationService.feedItems(userId, FEED_SIZE);
        Double ownShare = null;
        Double randomShare = null;
        Map<String, Object> feedBlock = new LinkedHashMap<>();
        feedBlock.put("size", feed.size());
        if (!feed.isEmpty()) {
            Set<Long> authors = new HashSet<>();
            Map<String, Long> feedTags = new HashMap<>();
            Map<String, Integer> reasons = new LinkedHashMap<>();
            int own = 0;
            int followed = 0;
            int inserted = 0;
            for (RecommendationService.FeedItem item : feed) {
                Post p = item.post();
                long author = p.getUser().getUserId();
                authors.add(author);
                if (members.contains(author)) {
                    own++;
                }
                if (item.followed()) {
                    followed++;
                }
                if (item.otherCommunity()) {
                    inserted++;
                }
                for (Tag t : p.getTags()) {
                    feedTags.merge(t.getName(), 1L, Long::sum);
                }
                reasons.merge(item.reason(), 1, Integer::sum);
            }
            if (myCommunity != null) {
                ownShare = own / (double) feed.size();
                randomShare = randomOwnShare(userId, members);
                feedBlock.put("ownCommunityShare", MyFeedInsights.round(ownShare));
                feedBlock.put("randomOwnCommunityShare", MyFeedInsights.round(randomShare));
            }
            feedBlock.put("followedShare", MyFeedInsights.round(followed / (double) feed.size()));
            feedBlock.put("otherCommunityPosts", inserted);
            feedBlock.put("distinctAuthors", authors.size());
            feedBlock.put("distinctTags", feedTags.size());
            feedBlock.put("topTags", MyFeedInsights.compareTags(feedTags, Map.of(), TOP_TAGS).stream()
                    .map(t -> Map.of("tag", t.tag(), "share", t.mine())).toList());
            feedBlock.put("reasons", reasons.entrySet().stream()
                    .map(e -> Map.of("reason", e.getKey(), "posts", e.getValue())).toList());
        }
        double outsideShare = weightsService.current().outsideShare();
        feedBlock.put("outsideShareSetting", outsideShare);
        result.put("feed", feedBlock);

        // 3. Що я вподобую: теми й настрій порівняно з мережею
        Map<String, Long> myTags = tagCounts("WHERE pl.user_id = ?", userId);
        Map<String, Long> networkTags = tagCounts("", null);
        List<MyFeedInsights.TagShare> tagShares = MyFeedInsights.compareTags(myTags, networkTags, TOP_TAGS);
        Integer likes = jdbc.queryForObject("SELECT COUNT(*) FROM post_likes WHERE user_id = ?", Integer.class, userId);
        int likeCount = likes == null ? 0 : likes;
        Map<String, Object> likesBlock = new LinkedHashMap<>();
        likesBlock.put("count", likeCount);
        likesBlock.put("tags", tagShares);
        Long vocabulary = jdbc.queryForObject("SELECT COUNT(DISTINCT tag_id) FROM post_tags", Long.class);
        likesBlock.put("topicHomogeneity", myTags.isEmpty() ? null : MyFeedInsights.round(
                CommunityAnalysisService.computeTopicHomogeneity(myTags, vocabulary == null ? 0 : vocabulary)));
        Map<String, Object> sentiment = new LinkedHashMap<>();
        sentiment.put("mine", MyFeedInsights.sentimentShares(jdbc.queryForList(
                "SELECT p.sentiment FROM post_likes pl JOIN posts p ON p.post_id = pl.post_id "
                        + "WHERE pl.user_id = ? AND p.sentiment IS NOT NULL", Double.class, userId)));
        sentiment.put("network", MyFeedInsights.sentimentShares(jdbc.queryForList(
                "SELECT sentiment FROM posts WHERE sentiment IS NOT NULL", Double.class)));
        likesBlock.put("sentiment", sentiment);
        result.put("likes", likesBlock);

        // 4. Обрані теми і висновки
        List<String> interests = interestRepository.findTagNames(userId);
        result.put("interests", interests);
        MyFeedInsights.TagShare top = tagShares.isEmpty() ? null : tagShares.get(0);
        result.put("hints", MyFeedInsights.hints(ownShare, randomShare, outsideShare,
                top == null ? null : top.mine(), top == null ? null : top.tag(), top == null ? null : top.network(),
                likeCount, interests.size()));
        return result;
    }

    private Map<String, Object> communityBlock(long userId, Long community, Set<Long> members) {
        if (community == null) {
            return null;
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("community", community);
        block.put("members", members.size());
        Optional<CommunityAnalysisDto> analysis = Optional.empty();
        try {
            analysis = communityAnalysisService.getCommunityForUser(userId, false);
        } catch (RuntimeException e) {
            log.warn("Аналіз спільноти недоступний: {}", e.getMessage());
        }
        analysis.ifPresent(a -> {
            block.put("topTags", a.getTopTags().stream().map(CommunityAnalysisDto.TagCount::tag).limit(3).toList());
            block.put("echoScore", MyFeedInsights.round(a.getEchoChamberScore()));
            block.put("topicHomogeneity", MyFeedInsights.round(a.getTopicHomogeneity()));
            block.put("semanticCohesion", a.getSemanticCohesion() == null ? null
                    : MyFeedInsights.round(a.getSemanticCohesion()));
            block.put("sentimentHomogeneity", MyFeedInsights.round(a.getSentimentHomogeneity()));
        });
        // ключові слова з текстів постів спільноти (TF-IDF відносно всієї мережі)
        Map<Long, List<String>> texts = communityData.textsByUser();
        CommunityKeywords.Index index = CommunityKeywords.index(texts.values().stream().flatMap(List::stream).toList());
        List<String> groupTexts = new ArrayList<>();
        members.forEach(u -> groupTexts.addAll(texts.getOrDefault(u, List.of())));
        block.put("keywords", CommunityKeywords.top(groupTexts, index, KEYWORDS).stream()
                .map(CommunityKeywords.Keyword::word).toList());
        // показник мережі з останнього знімка — для порівняння
        List<UserEchoPointDto> history = echoHistoryService.userHistory(userId, false, 1);
        if (!history.isEmpty()) {
            block.put("networkEchoScore", MyFeedInsights.round(history.get(0).networkEchoScore()));
        }
        return block;
    }

    /** Частка постів своєї спільноти серед усіх чужих постів мережі — така була б у стрічці з випадкових постів. */
    private double randomOwnShare(long userId, Set<Long> members) {
        Long all = jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE user_id <> ?", Long.class, userId);
        if (all == null || all == 0) {
            return 0;
        }
        Long[] ids = members.stream().filter(u -> u != userId).toArray(Long[]::new);
        Long own = jdbc.query(con -> {
            var ps = con.prepareStatement("SELECT COUNT(*) FROM posts WHERE user_id = ANY(?)");
            ps.setArray(1, con.createArrayOf("bigint", ids));
            return ps;
        }, rs -> rs.next() ? rs.getLong(1) : 0L);
        return (own == null ? 0 : own) / (double) all;
    }

    /** Тег → скільки разів він трапився серед вподобаних постів (усіх або однієї людини). */
    private Map<String, Long> tagCounts(String where, Long userId) {
        Map<String, Long> result = new HashMap<>();
        String sql = "SELECT t.name, COUNT(*) AS n FROM post_likes pl JOIN post_tags pt ON pt.post_id = pl.post_id "
                + "JOIN tags t ON t.tag_id = pt.tag_id " + where + " GROUP BY t.name";
        RowCallbackHandler handler = rs -> result.put(rs.getString("name"), rs.getLong("n"));
        if (userId == null) {
            jdbc.query(sql, handler);
        } else {
            jdbc.query(sql, handler, userId);
        }
        return result;
    }
}
