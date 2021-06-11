package com.commercetools.paymenttoorderprocessor.paymentcreationconfigurationmanager;

import com.commercetools.paymenttoorderprocessor.dto.PaymentTransactionCreatedOrUpdatedMessage;
import io.sphere.sdk.payments.Payment;
import io.sphere.sdk.payments.TransactionState;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

public class PaymentCreationConfigurationManagerImpl implements PaymentCreationConfigurationManager {

    @Value("${createorder.createorderon}")
    private String[] transactionTypes;

    @Override
    public boolean isTransactionSuccessAndHasMatchingTransactionTypes(final PaymentTransactionCreatedOrUpdatedMessage message, final Payment payment) {
        final String transactionID = message.getTransactionId();
        final TransactionState stateFromMessage = message.getState();
        return TransactionState.SUCCESS.equals(stateFromMessage)
                && (payment.getTransactions().stream().anyMatch(
                transaction -> transactionID.equals(transaction.getId())
                        && Arrays.stream(transactionTypes).anyMatch(type -> equalsIgnoreCase(type, transaction.getType().toString()))));
    }
}
