package com.commercetools.paymenttoorderprocessor.paymentcreationconfigurationmanager;

import com.commercetools.paymenttoorderprocessor.dto.PaymentTransactionCreatedOrUpdatedMessage;
import io.sphere.sdk.payments.Payment;

public interface PaymentCreationConfigurationManager {
    boolean isTransactionSuccessAndHasMatchingTransactionTypes(PaymentTransactionCreatedOrUpdatedMessage message, Payment payment);
}
