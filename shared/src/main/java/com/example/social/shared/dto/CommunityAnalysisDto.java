package com.example.social.shared.dto;

import java.util.List;

public class CommunityAnalysisDto {

    public record TagCount(String tag, long count) {}

    private long community;
    private int memberCount;
    private int postCount;
    private List<TagCount> topTags;
    private double topicHomogeneity;
    private Double semanticCohesion;
    private double avgSentiment;
    private double sentimentHomogeneity;
    private double echoChamberScore;

    public CommunityAnalysisDto(long community, int memberCount, int postCount,
                                List<TagCount> topTags, double topicHomogeneity,
                                Double semanticCohesion, double avgSentiment,
                                double sentimentHomogeneity, double echoChamberScore) {
        this.community = community;
        this.memberCount = memberCount;
        this.postCount = postCount;
        this.topTags = topTags;
        this.topicHomogeneity = topicHomogeneity;
        this.semanticCohesion = semanticCohesion;
        this.avgSentiment = avgSentiment;
        this.sentimentHomogeneity = sentimentHomogeneity;
        this.echoChamberScore = echoChamberScore;
    }

    public long getCommunity() { return community; }
    public int getMemberCount() { return memberCount; }
    public int getPostCount() { return postCount; }
    public List<TagCount> getTopTags() { return topTags; }
    public double getTopicHomogeneity() { return topicHomogeneity; }
    public Double getSemanticCohesion() { return semanticCohesion; }
    public double getAvgSentiment() { return avgSentiment; }
    public double getSentimentHomogeneity() { return sentimentHomogeneity; }
    public double getEchoChamberScore() { return echoChamberScore; }
}