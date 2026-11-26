package com.example.social.server.controller;

import com.example.social.server.exception.BadRequestException;
import com.example.social.server.service.CommunityQualityService;
import com.example.social.server.service.CommunityStructureService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Аналіз спільнот поза ехо-камерами (лише для адміністратора: /api/graph/** і /api/dev/**):
 * якість виявлення на відомих спільнотах генератора, структура спільнот, лідери, «мости»
 * й зв'язки між спільнотами.
 */
@RestController
public class CommunityInsightController {

    private final CommunityQualityService qualityService;
    private final CommunityStructureService structureService;

    public CommunityInsightController(CommunityQualityService qualityService,
                                      CommunityStructureService structureService) {
        this.qualityService = qualityService;
        this.structureService = structureService;
    }

    /** NMI, ARI, чистота Louvain і Leiden на основній згенерованій мережі. */
    @GetMapping("/api/graph/communities/quality")
    public ResponseEntity<Map<String, Object>> quality(
            @RequestParam(name = "refresh", defaultValue = "false") boolean refresh) {
        return ResponseEntity.ok(qualityService.current(refresh));
    }

    /** Структура спільнот: щільність, провідність, теми, лідери, «мости», матриця підписок. */
    @GetMapping("/api/graph/communities/structure")
    public ResponseEntity<Map<String, Object>> structure(
            @RequestParam(name = "algorithm", defaultValue = "louvain") String algorithm,
            @RequestParam(name = "top", defaultValue = "10") int top,
            @RequestParam(name = "refresh", defaultValue = "false") boolean refresh) {
        if (!algorithm.equals("louvain") && !algorithm.equals("leiden")) {
            throw new BadRequestException("algorithm: louvain або leiden");
        }
        if (top < 1 || top > 30) {
            throw new BadRequestException("top: від 1 до 30");
        }
        return ResponseEntity.ok(structureService.structure(algorithm.equals("leiden"), top, refresh));
    }

    /**
     * Louvain проти Leiden на тимчасових мережах з різною гомофілією. Основна мережа не змінюється,
     * але її спільноти наприкінці визначаються заново. Триває кілька хвилин.
     * За замовчуванням гомофілія від 0.15 (майже випадкові підписки: з 8 спільнот «своя» — 1/8) до 0.9,
     * щоб було видно поріг, нижче якого спільноти вже неможливо відновити.
     * Приклад: POST /api/dev/community-quality-experiment?homophilies=0.2,0.3,0.5&repeats=3
     */
    @PostMapping("/api/dev/community-quality-experiment")
    public ResponseEntity<Map<String, Object>> experiment(
            @RequestParam(name = "users", defaultValue = "200") int users,
            @RequestParam(name = "communities", defaultValue = "8") int communities,
            @RequestParam(name = "homophilies", defaultValue = "0.15,0.2,0.25,0.3,0.35,0.4,0.5,0.7,0.9") List<Double> homophilies,
            @RequestParam(name = "repeats", defaultValue = "3") int repeats,
            @RequestParam(name = "seed", defaultValue = "100") long seed) {
        return ResponseEntity.ok(qualityService.experiment(users, communities, homophilies, repeats, seed));
    }
}