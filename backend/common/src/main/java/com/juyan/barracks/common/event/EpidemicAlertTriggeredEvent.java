package com.juyan.barracks.common.event;

import com.juyan.barracks.common.entity.EpidemicAlert;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class EpidemicAlertTriggeredEvent extends ApplicationEvent {

    private final EpidemicAlert alert;
    private final String alertLevel;
    private final Double positiveRate;

    public EpidemicAlertTriggeredEvent(Object source, EpidemicAlert alert) {
        super(source);
        this.alert = alert;
        this.alertLevel = alert.getAlertLevel();
        this.positiveRate = alert.getPositiveRate().doubleValue();
    }
}
