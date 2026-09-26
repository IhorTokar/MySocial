package com.example.social.shared.dto;

import java.time.LocalDateTime;
import java.util.List;

public class RecommendationDto {

    private Long postId;
    private String authorUsername;
    private String label;
    private String text;
    private LocalDateTime createdDate;
    private List<String> tags;

    private long likesInWindow;
    private double popularityScore;
    private double itemCfScore;
    private double graphSimScore;
    private double semanticScore;
    private double recencyScore;
    private long postAgeHours;
    private boolean followed;
    private double followedBonus;
    private double totalScore;

    public RecommendationDto(Long postId, String authorUsername, String label, String text,
                             LocalDateTime createdDate, List<String> tags,
                             long likesInWindow, double popularityScore, double itemCfScore,
                             double graphSimScore, double semanticScore, double recencyScore,
                             long postAgeHours, boolean followed, double followedBonus, double totalScore) {
        this.postId = postId;
        this.authorUsername = authorUsername;
        this.label = label;
        this.text = text;
        this.createdDate = createdDate;
        this.tags = tags;
        this.likesInWindow = likesInWindow;
        this.popularityScore = popularityScore;
        this.itemCfScore = itemCfScore;
        this.graphSimScore = graphSimScore;
        this.semanticScore = semanticScore;
        this.recencyScore = recencyScore;
        this.postAgeHours = postAgeHours;
        this.followed = followed;
        this.followedBonus = followedBonus;
        this.totalScore = totalScore;
    }

    public Long getPostId() { return postId; }
    public String getAuthorUsername() { return authorUsername; }
    public String getLabel() { return label; }
    public String getText() { return text; }
    public LocalDateTime getCreatedDate() { return createdDate; }
    public List<String> getTags() { return tags; }
    public long getLikesInWindow() { return likesInWindow; }
    public double getPopularityScore() { return popularityScore; }
    public double getItemCfScore() { return itemCfScore; }
    public double getGraphSimScore() { return graphSimScore; }
    public double getSemanticScore() { return semanticScore; }
    public double getRecencyScore() { return recencyScore; }
    public long getPostAgeHours() { return postAgeHours; }
    public boolean isFollowed() { return followed; }
    public double getFollowedBonus() { return followedBonus; }
    public double getTotalScore() { return totalScore; }
}