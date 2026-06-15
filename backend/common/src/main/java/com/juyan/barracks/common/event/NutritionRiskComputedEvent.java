package com.juyan.barracks.common.event;

import com.juyan.barracks.common.entity.NutritionRisk;
import com.juyan.barracks.common.entity.Soldier;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NutritionRiskComputedEvent extends ApplicationEvent {

    private final Soldier soldier;
    private final NutritionRisk nutritionRisk;

    public NutritionRiskComputedEvent(Object source, Soldier soldier, NutritionRisk nutritionRisk) {
        super(source);
        this.soldier = soldier;
        this.nutritionRisk = nutritionRisk;
    }
}
