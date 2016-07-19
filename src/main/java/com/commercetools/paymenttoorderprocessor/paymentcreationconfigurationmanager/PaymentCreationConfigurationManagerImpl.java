package com.commercetools.paymenttoorderprocessor.paymentcreationconfigurationmanager;

import io.sphere.sdk.payments.TransactionState;
import io.sphere.sdk.payments.messages.PaymentTransactionStateChangedMessage;

public class PaymentCreationConfigurationManagerImpl implements PaymentCreationConfigurationManager {

    @Override
    public boolean doesTransactionStateMatchConfiguration(PaymentTransactionStateChangedMessage message) {
        return TransactionState.SUCCESS.equals(message.getState());
    }

}
