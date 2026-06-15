package com.juyan.barracks.alert.service;

import com.juyan.barracks.common.dto.SoldierWithRiskDTO;
import com.juyan.barracks.common.entity.MealRecord;
import com.juyan.barracks.common.entity.NutritionRisk;
import com.juyan.barracks.common.entity.Soldier;
import com.juyan.barracks.common.repository.MealRecordRepository;
import com.juyan.barracks.common.repository.NutritionRiskRepository;
import com.juyan.barracks.common.repository.SoldierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SoldierService {

    private final SoldierRepository soldierRepository;
    private final NutritionRiskRepository nutritionRiskRepository;
    private final MealRecordRepository mealRecordRepository;

    public List<Soldier> findAll() {
        return soldierRepository.findAll();
    }

    public Optional<Soldier> findById(Long id) {
        return soldierRepository.findById(id);
    }

    public List<Soldier> findByBarracksId(Long barracksId) {
        return soldierRepository.findByBarracksId(barracksId);
    }

    public List<SoldierWithRiskDTO> findAllWithRisk() {
        List<Soldier> soldiers = soldierRepository.findAll();
        return soldiers.stream().map(this::enrichWithRisk).collect(Collectors.toList());
    }

    public List<SoldierWithRiskDTO> findByBarracksIdWithRisk(Long barracksId) {
        List<Soldier> soldiers = soldierRepository.findByBarracksId(barracksId);
        return soldiers.stream().map(this::enrichWithRisk).collect(Collectors.toList());
    }

    public SoldierWithRiskDTO enrichWithRisk(Soldier soldier) {
        NutritionRisk risk = nutritionRiskRepository.findBySoldierIdAndIsCurrentTrue(soldier.getId()).orElse(null);

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(23, 59, 59);

        BigDecimal proteinG = mealRecordRepository.sumProteinBySoldierAndTime(soldier.getId(), startOfDay, endOfDay);
        BigDecimal fatG = mealRecordRepository.sumFatBySoldierAndTime(soldier.getId(), startOfDay, endOfDay);
        BigDecimal vitaminCMg = mealRecordRepository.sumVitaminCBySoldierAndTime(soldier.getId(), startOfDay, endOfDay);
        BigDecimal calorieKcal = mealRecordRepository.sumCalorieBySoldierAndTime(soldier.getId(), startOfDay, endOfDay);

        return SoldierWithRiskDTO.builder()
                .soldierId(soldier.getId())
                .soldierCode(soldier.getSoldierCode())
                .name(soldier.getName())
                .age(soldier.getAge())
                .rank(soldier.getRank())
                .originRegion(soldier.getOriginRegion())
                .positionX(soldier.getPositionX())
                .positionY(soldier.getPositionY())
                .status(soldier.getStatus())
                .barracksId(soldier.getBarracksId())
                .riskLevel(risk != null ? risk.getRiskLevel() : "LOW")
                .overallRiskScore(risk != null ? risk.getOverallRiskScore() : BigDecimal.ZERO)
                .vitaminCRiskScore(risk != null ? risk.getVitaminCRiskScore() : BigDecimal.ZERO)
                .proteinRiskScore(risk != null ? risk.getProteinRiskScore() : BigDecimal.ZERO)
                .fatRiskScore(risk != null ? risk.getFatRiskScore() : BigDecimal.ZERO)
                .dietarySuggestion(risk != null ? risk.getDietarySuggestion() : null)
                .dailyProteinG(proteinG != null ? proteinG : BigDecimal.ZERO)
                .dailyFatG(fatG != null ? fatG : BigDecimal.ZERO)
                .dailyVitaminCMg(vitaminCMg != null ? vitaminCMg : BigDecimal.ZERO)
                .dailyCalorieKcal(calorieKcal != null ? calorieKcal : BigDecimal.ZERO)
                .build();
    }
}
