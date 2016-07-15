package com.commercetools.paymenttoorderprocessor.timestamp;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface TimeStampManager {
    public Optional<ZonedDateTime> getLastProcessedMessageTimeStamp();
    public void setActualProcessedMessageTimeStamp(ZonedDateTime timeStamp);
    public void persistLastProcessedMessageTimeStamp();
}
