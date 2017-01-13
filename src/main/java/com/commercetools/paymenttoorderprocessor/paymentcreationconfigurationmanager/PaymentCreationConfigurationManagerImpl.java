package com.commercetools.paymenttoorderprocessor.paymentcreationconfigurationmanager;

import io.sphere.sdk.payments.Payment;
import io.sphere.sdk.payments.TransactionState;
import io.sphere.sdk.payments.messages.PaymentTransactionStateChangedMessage;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;

public class PaymentCreationConfigurationManagerImpl implements PaymentCreationConfigurationManager {

    @Value("${createorder.createorderon}")
    private String[] transactionTypes;

    @Override
    public boolean doesTransactionStateMatchConfiguration(final PaymentTransactionStateChangedMessage message, final Payment payment) {
        final String transactionID = message.getTransactionId();
        return TransactionState.SUCCESS.equals(message.getState())
                && (payment.getTransactions().stream().anyMatch(
                transaction -> transactionID.equals(transaction.getId())
                        && Arrays.stream(transactionTypes).anyMatch(type -> type.equals(transaction.getType().toString()))));
    }
}
