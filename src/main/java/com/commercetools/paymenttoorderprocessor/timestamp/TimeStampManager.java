package com.commercetools.paymenttoorderprocessor.timestamp;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Persists the Timestamp for the last successfully processed Message in the Job.
 * @author mht@dotsource.de
 *
 */
public interface TimeStampManager {
    public Optional<ZonedDateTime> getLastProcessedMessageTimeStamp();
    public void setActualProcessedMessageTimeStamp(ZonedDateTime timeStamp);
    public void persistLastProcessedMessageTimeStamp();
    public void processingMessageFailed();
}
