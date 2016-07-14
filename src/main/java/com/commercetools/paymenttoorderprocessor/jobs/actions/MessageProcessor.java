package com.commercetools.paymenttoorderprocessor.jobs.actions;

import org.springframework.batch.item.ItemProcessor;

import io.sphere.sdk.messages.Message;
import io.sphere.sdk.payments.TransactionState;
import io.sphere.sdk.payments.messages.PaymentTransactionStateChangedMessage;


public class MessageProcessor implements ItemProcessor<Message, Message> {
    @Override
    public Message process(Message message) throws Exception {
        return filter(message);
    }

    private PaymentTransactionStateChangedMessage filter(final Message message) {
        if (message instanceof PaymentTransactionStateChangedMessage){
            final PaymentTransactionStateChangedMessage paymentTransactionStateChangedMessage = (PaymentTransactionStateChangedMessage)message;
            if (TransactionState.SUCCESS.equals(paymentTransactionStateChangedMessage.getState())) {
                return paymentTransactionStateChangedMessage;
            }
        }

        return null;
    }
}
