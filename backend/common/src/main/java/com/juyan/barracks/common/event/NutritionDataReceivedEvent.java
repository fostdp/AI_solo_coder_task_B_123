package com.juyan.barracks.common.event;

import com.juyan.barracks.common.dto.NutritionSensorMessage;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NutritionDataReceivedEvent extends ApplicationEvent {

    private final NutritionSensorMessage message;
    private final Long nutritionDataId;

    public NutritionDataReceivedEvent(Object source, NutritionSensorMessage message, Long nutritionDataId) {
        super(source);
        this.message = message;
        this.nutritionDataId = nutritionDataId;
    }
}
