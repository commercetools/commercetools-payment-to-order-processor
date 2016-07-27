package com.commercetools.paymenttoorderprocessor.customobjects;

import io.sphere.sdk.messages.Message;

public interface MessageProcessedManager {

    public boolean isMessageUnprocessed(Message message);
    public void setMessageIsProcessed(Message message);

}
