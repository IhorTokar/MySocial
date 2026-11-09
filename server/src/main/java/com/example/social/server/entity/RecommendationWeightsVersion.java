package com.example.social.server.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Одна версія вагових коефіцієнтів стрічки (рядок історії змін). */
@Entity
@Table(name = "recommendation_weights")
public class RecommendationWeightsVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "weights_id")
    private Long weightsId;

    @Column(nullable = false)
    private double popularity;

    @Column(name = "item_cf", nullable = false)
    private double itemCf;

    @Column(name = "graph_sim", nullable = false)
    private double graphSim;

    @Column(nullable = false)
    private double semantic;

    @Column(nullable = false)
    private double recency;

    @Column(name = "followed_bonus", nullable = false)
    private double followedBonus;

    /** λ для MMR-перестановки стрічки: 1 — без перестановки, менше — різноманітніша стрічка. */
    @Column(name = "mmr_lambda", nullable = false)
    private double mmrLambda;

    @Column(length = 255)
    private String comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected RecommendationWeightsVersion() {
    }

    public RecommendationWeightsVersion(double popularity, double itemCf, double graphSim,
                                        double semantic, double recency, double followedBonus,
                                        double mmrLambda, String comment, User createdBy) {
        this.popularity = popularity;
        this.itemCf = itemCf;
        this.graphSim = graphSim;
        this.semantic = semantic;
        this.recency = recency;
        this.followedBonus = followedBonus;
        this.mmrLambda = mmrLambda;
        this.comment = comment;
        this.createdBy = createdBy;
    }

    public Long getWeightsId() { return weightsId; }
    public double getPopularity() { return popularity; }
    public double getItemCf() { return itemCf; }
    public double getGraphSim() { return graphSim; }
    public double getSemantic() { return semantic; }
    public double getRecency() { return recency; }
    public double getFollowedBonus() { return followedBonus; }
    public String getComment() { return comment; }
    public User getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public double getMmrLambda() { return mmrLambda; }
}