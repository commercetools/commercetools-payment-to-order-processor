package com.commercetools.paymenttoorderprocessor.jobs.actions;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;

import io.sphere.sdk.carts.Cart;
import io.sphere.sdk.carts.queries.CartQuery;
import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.messages.Message;
import io.sphere.sdk.payments.Payment;
import io.sphere.sdk.payments.TransactionState;
import io.sphere.sdk.payments.messages.PaymentTransactionStateChangedMessage;
import io.sphere.sdk.payments.queries.PaymentByIdGet;


public class MessageProcessor implements ItemProcessor<Message, Cart> {
    public static final Logger LOG = LoggerFactory.getLogger(MessageProcessor.class);
    
    @Autowired
    private BlockingSphereClient client;
    
    private Cart cart;
    private PaymentTransactionStateChangedMessage message;
    private Payment payment;
    
    @Override
    public Cart process(Message message) {
        LOG.info("Called MessageProcesser.process with parameter {}", message);
        if (message instanceof PaymentTransactionStateChangedMessage){
            this.message = (PaymentTransactionStateChangedMessage)message;
            setCartIfPaymentTransactionIsConfigured();
            return cart;
        }
        else {
            return null;
        }
    }

    private void setCartIfPaymentTransactionIsConfigured() {
        if (TransactionState.SUCCESS.equals(message.getState())) {
            getCorrespondingPaymentAndCart();
        }
    }

    private void getCorrespondingPaymentAndCart() {
        final String paymentId = message.getResource().getId();
        LOG.info("Query CTP for Payment with ID {}", paymentId);
        final PaymentByIdGet paymentByIdGet = PaymentByIdGet.of(paymentId);
        payment = client.executeBlocking(paymentByIdGet);
        final CartQuery cartQuery = CartQuery.of()
                .withPredicates(m -> m.paymentInfo().payments().isIn(Collections.singletonList(payment)));
        List<Cart> results = client.executeBlocking(cartQuery).getResults();
        if (results.isEmpty()){
            cart = null;
        }
        else {
            //assume one payment is not assinged to multipe carts
            cart = results.get(0);
        }
        LOG.info("Got Payment {} and Cart {} from Query for Payment ID {}", payment, cart, paymentId);
    }
}
