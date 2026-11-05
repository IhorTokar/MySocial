package com.example.social.server.repository;

import com.example.social.server.entity.RecommendationWeightsVersion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecommendationWeightsRepository extends JpaRepository<RecommendationWeightsVersion, Long> {

    /** Остання (чинна) версія ваг. */
    Optional<RecommendationWeightsVersion> findTopByOrderByWeightsIdDesc();

    /** Історія змін, від найновішої. */
    List<RecommendationWeightsVersion> findAllByOrderByWeightsIdDesc(Pageable pageable);
}