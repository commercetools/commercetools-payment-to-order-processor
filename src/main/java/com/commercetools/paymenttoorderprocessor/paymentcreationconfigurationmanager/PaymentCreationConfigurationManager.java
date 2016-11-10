package com.commercetools.paymenttoorderprocessor.paymentcreationconfigurationmanager;

import io.sphere.sdk.payments.Payment;
import io.sphere.sdk.payments.messages.PaymentTransactionStateChangedMessage;

public interface PaymentCreationConfigurationManager {
    boolean doesTransactionStateMatchConfiguration(PaymentTransactionStateChangedMessage message, Payment payment);
}
