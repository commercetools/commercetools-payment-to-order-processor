package com.commercetools.paymenttoorderprocessor.paymentcreationconfigurationmanager;

import io.sphere.sdk.payments.messages.PaymentTransactionStateChangedMessage;

public interface PaymentCreationConfigurationManager {
    public boolean doesTransactionStateMatchConfiguration(PaymentTransactionStateChangedMessage message);
    
}
