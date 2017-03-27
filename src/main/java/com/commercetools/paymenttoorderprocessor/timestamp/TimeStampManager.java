package com.commercetools.paymenttoorderprocessor.timestamp;

import javax.annotation.Nullable;
import java.time.ZonedDateTime;

/**
 * Persists the Timestamp for the last successfully processed Message in the Job.
 *
 * @author mht@dotsource.de
 */
public interface TimeStampManager {

    @Nullable
    public ZonedDateTime getLastProcessedMessageTimeStamp();

    public void setActualProcessedMessageTimeStamp(ZonedDateTime timeStamp);

    public void persistLastProcessedMessageTimeStamp();

    public void processingMessageFailed();
}
