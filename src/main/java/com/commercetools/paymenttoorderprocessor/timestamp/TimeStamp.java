package com.commercetools.paymenttoorderprocessor.timestamp;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.sphere.sdk.models.Base;

public class TimeStamp extends Base {
    private final ZonedDateTime lastTimeStamp;
    
    @JsonCreator
    private TimeStamp(final ZonedDateTime lastTimeStamp) {
        this.lastTimeStamp = lastTimeStamp;
    }
    
    public ZonedDateTime getLastTimeStamp(){
        return lastTimeStamp;
    }
}
