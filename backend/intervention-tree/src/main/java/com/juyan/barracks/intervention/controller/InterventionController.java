package com.juyan.barracks.intervention.controller;

import com.juyan.barracks.common.entity.InterventionRecommendation;
import com.juyan.barracks.common.repository.InterventionRecommendationRepository;
import com.juyan.barracks.intervention.algorithm.SupplementCatalog;
import com.juyan.barracks.intervention.dto.RecommendationResultDTO;
import com.juyan.barracks.intervention.dto.TreeTrainResultDTO;
import com.juyan.barracks.intervention.service.NutritionInterventionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping
@RequiredArgsConstructor
public class InterventionController {

    private final NutritionInterventionService interventionService;
    private final InterventionRecommendationRepository recommendationRepository;

    @GetMapping("/v1/intervention-rules")
    public ResponseEntity<Map<String, Object>> getInterventionRules() {
        log.info("查询决策树规则");
        Map<String, Object> result = new HashMap<>();

        List<String> treeRules = interventionService.getDecisionTreeRules();
        result.put("treeTrained", interventionService.isTreeTrained());
        result.put("treeRuleCount", treeRules.size());
        result.put("treeRules", treeRules);

        List<String> builtinRules = List.of(
                "RULE-001: VitaminCRisk > 0.7 → 干枣 + 新鲜蔬菜",
                "RULE-002: ProteinRisk > 0.7 → 肉干 + 豆制品",
                "RULE-003: FatRisk > 0.7 → 坚果",
                "RULE-004: Age > 40 → 鱼肝油",
                "RULE-005: 南方士兵 → 增加豆制品比例(+30%)",
                "RULE-006: 北方士兵 → 增加蔬菜比例(+30%)"
        );
        result.put("builtinRules", builtinRules);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/v1/intervention-recommendations")
    public ResponseEntity<List<InterventionRecommendation>> getRecommendations(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long soldierId) {
        log.info("查询推荐列表: status={}, soldierId={}", status, soldierId);

        List<InterventionRecommendation> result;

        if (soldierId != null) {
            result = recommendationRepository.findBySoldierIdOrderByGeneratedAtDesc(soldierId);
        } else if ("PENDING".equalsIgnoreCase(status)) {
            result = recommendationRepository.findPendingRecommendations();
        } else {
            result = recommendationRepository.findTop50ByOrderByGeneratedAtDesc();
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/v1/intervention-recommendations/{id}")
    public ResponseEntity<InterventionRecommendation> getRecommendationById(@PathVariable Long id) {
        log.info("查询推荐详情: id={}", id);
        Optional<InterventionRecommendation> opt = recommendationRepository.findById(id);
        return opt.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/v1/intervention-recommendations/generate/{soldierId}")
    public ResponseEntity<?> generateForSoldier(@PathVariable Long soldierId) {
        log.info("单人生成推荐: soldierId={}", soldierId);
        try {
            RecommendationResultDTO result = interventionService.generateRecommendation(soldierId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (IllegalStateException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("生成推荐失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "生成失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/v1/intervention-recommendations/generate-all")
    public ResponseEntity<Map<String, Object>> generateForHighRisk(
            @RequestParam(required = false) Long barracksId,
            @RequestParam(required = false) Double riskThreshold) {
        log.info("批量生成高风险推荐: barracksId={}, riskThreshold={}", barracksId, riskThreshold);

        Map<String, Object> result = new HashMap<>();
        try {
            List<RecommendationResultDTO> recommendations =
                    interventionService.generateForHighRisk(barracksId, riskThreshold);

            result.put("success", true);
            result.put("barracksId", barracksId);
            result.put("riskThreshold", riskThreshold);
            result.put("generatedCount", recommendations.size());
            result.put("recommendations", recommendations);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("批量生成推荐失败", e);
            result.put("success", false);
            result.put("message", "批量生成失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @PostMapping("/v1/intervention-tree/retrain")
    public ResponseEntity<TreeTrainResultDTO> retrainTree() {
        log.info("重新训练决策树");
        try {
            TreeTrainResultDTO result = interventionService.trainTreeFromHistory();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("训练决策树失败", e);
            TreeTrainResultDTO result = new TreeTrainResultDTO();
            result.setSuccess(false);
            result.setMessage("训练失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/v1/supplement-catalog")
    public ResponseEntity<Map<String, Object>> getSupplementCatalog() {
        log.info("查询补给品目录");

        Map<String, Object> result = new HashMap<>();
        List<SupplementCatalog.Supplement> supplements = SupplementCatalog.getAllSupplements();

        result.put("totalCount", supplements.size());
        result.put("supplements", supplements);

        Map<String, Object> categories = new HashMap<>();
        categories.put("vitaminC", SupplementCatalog.getVitaminCSupplements());
        categories.put("protein", SupplementCatalog.getProteinSupplements());
        categories.put("fat", SupplementCatalog.getFatSupplements());
        categories.put("elderly", SupplementCatalog.getElderlySupplements());
        result.put("byCategory", categories);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/v1/intervention-tree/status")
    public ResponseEntity<Map<String, Object>> getTreeStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("treeTrained", interventionService.isTreeTrained());
        status.put("ruleCount", interventionService.getDecisionTreeRules().size());
        return ResponseEntity.ok(status);
    }
}
