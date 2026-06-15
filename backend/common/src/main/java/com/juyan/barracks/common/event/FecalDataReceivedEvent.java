package com.juyan.barracks.common.event;

import com.juyan.barracks.common.dto.FecalSensorMessage;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class FecalDataReceivedEvent extends ApplicationEvent {

    private final FecalSensorMessage message;
    private final Long fecalDataId;

    public FecalDataReceivedEvent(Object source, FecalSensorMessage message, Long fecalDataId) {
        super(source);
        this.message = message;
        this.fecalDataId = fecalDataId;
    }
}
