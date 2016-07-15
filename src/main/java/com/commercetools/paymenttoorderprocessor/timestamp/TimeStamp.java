package com.commercetools.paymenttoorderprocessor.timestamp;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.sphere.sdk.models.Base;

public class TimeStamp extends Base {
    private final ZonedDateTime lastTimeStamp;
    
    @JsonCreator
    public TimeStamp(@JsonProperty("lastTimeStamp") final ZonedDateTime lastTimeStamp) {
        this.lastTimeStamp = lastTimeStamp;
    }
    
    public ZonedDateTime getLastTimeStamp(){
        return lastTimeStamp;
    }
}
