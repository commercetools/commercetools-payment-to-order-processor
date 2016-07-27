package com.commercetools.paymenttoorderprocessor.jobs.actions;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.money.MonetaryAmount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;

import com.commercetools.paymenttoorderprocessor.customobjects.MessageProcessedManager;
import com.commercetools.paymenttoorderprocessor.paymentcreationconfigurationmanager.PaymentCreationConfigurationManager;
import com.commercetools.paymenttoorderprocessor.wrapper.CartAndMessage;

import io.sphere.sdk.carts.Cart;
import io.sphere.sdk.carts.CartState;
import io.sphere.sdk.carts.queries.CartQuery;
import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.payments.Payment;
import io.sphere.sdk.payments.Transaction;
import io.sphere.sdk.payments.messages.PaymentTransactionStateChangedMessage;
import io.sphere.sdk.payments.queries.PaymentByIdGet;

/***
 * Checks if PaymentTransactionStateChangedMessage and corresponding Cart is viable for creation of an Order
 * @author mht@dotsource.de
 *
 */
public class MessageFilter implements ItemProcessor<PaymentTransactionStateChangedMessage, CartAndMessage> {
    public static final Logger LOG = LoggerFactory.getLogger(MessageFilter.class);
    
    @Autowired
    private BlockingSphereClient client;
    
    @Autowired
    private MessageProcessedManager messageProcessedManager;
    
    @Autowired
    private PaymentCreationConfigurationManager paymentCreationConfigurationManager;
    
    private Cart cart;
    private PaymentTransactionStateChangedMessage message;
    private Payment payment;

    @Override
    public CartAndMessage process(PaymentTransactionStateChangedMessage message) {
        LOG.debug("Called MessageFilter.process with parameter {}", message);
        this.message = message;
        getCartIfPaymentTransactionIsConfigured();
        if(isCartAmountEqualToTransaction()) {
            return new CartAndMessage(cart, message);
        }
        else {
            messageProcessedManager.setMessageIsProcessed(message);
            return null;
        }
    }

    private boolean isCartAmountEqualToTransaction() {
        if (cart != null && cart.getCartState() != CartState.ORDERED) {
            final MonetaryAmount cartAmount = cart.getTotalPrice();
            final Optional<Transaction> transaction = payment
                    .getTransactions().stream().filter(t -> t.getId().equals(message.getTransactionId())).findFirst();
            return (cartAmount.equals(transaction.isPresent() ? transaction.get().getAmount() : null));
        }
        return false;
    }

    private void getCartIfPaymentTransactionIsConfigured() {
        if (paymentCreationConfigurationManager.doesTransactionStateMatchConfiguration(message)) {
            getCorrespondingPaymentAndCart();
        }
    }

    private void getCorrespondingPaymentAndCart() {
        final String paymentId = message.getResource().getId();
        LOG.debug("Query CTP for Payment with ID {}", paymentId);
        final PaymentByIdGet paymentByIdGet = PaymentByIdGet.of(paymentId);
        payment = client.executeBlocking(paymentByIdGet);
        if (payment != null) {
            final CartQuery cartQuery = CartQuery.of()
                    .withPredicates(m -> m.paymentInfo().payments().isIn(Collections.singletonList(payment)));
            List<Cart> results = client.executeBlocking(cartQuery).getResults();
            if (results.isEmpty()){
                cart = null;
            }
            else {
                //assume one payment is not assigned to multiple carts
                cart = results.get(0);
            }
        }
        else {
            cart = null;
        }
        LOG.info("Got Payment {} and Cart {} from Query for Payment ID {}", payment, cart, paymentId);
    }
}
