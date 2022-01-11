package com.commercetools.paymenttoorderprocessor.jobs.actions;

import com.commercetools.paymenttoorderprocessor.customobjects.MessageProcessedManager;
import com.commercetools.paymenttoorderprocessor.dto.PaymentTransactionCreatedOrUpdatedMessage;
import com.commercetools.paymenttoorderprocessor.paymentcreationconfigurationmanager.PaymentCreationConfigurationManager;
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;
import com.commercetools.paymenttoorderprocessor.wrapper.CartAndMessage;
import io.sphere.sdk.carts.Cart;
import io.sphere.sdk.carts.CartState;
import io.sphere.sdk.carts.queries.CartQuery;
import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.payments.Payment;
import io.sphere.sdk.payments.Transaction;
import io.sphere.sdk.payments.queries.PaymentByIdGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Nullable;
import javax.money.MonetaryAmount;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/***
 * Checks if PaymentTransactionCreatedOrUpdatedMessage and corresponding Cart is viable for creation of an Order
 * @author mht@dotsource.de
 *
 */
public class MessageFilter implements ItemProcessor<PaymentTransactionCreatedOrUpdatedMessage, CartAndMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(MessageFilter.class);

    @Autowired
    private BlockingSphereClient client;

    @Autowired
    private MessageProcessedManager messageProcessedManager;

    @Autowired
    private PaymentCreationConfigurationManager paymentCreationConfigurationManager;

    @Autowired
    private TimeStampManager timeStampManager;

    @Override
    public CartAndMessage process(PaymentTransactionCreatedOrUpdatedMessage message) {
        LOG.debug("Called MessageFilter.process with parameter {}", message);
        final Payment payment = getCorrespondingPayment(message);
        if (payment != null) {
            if (paymentCreationConfigurationManager.isTransactionSuccessAndHasMatchingTransactionTypes(message, payment)) {
                final Optional<Cart> oCart = getCorrespondingCart(payment);
                if (oCart.isPresent()) {
                    final Cart cart = oCart.get();
                    if (cart.getCartState() != CartState.ORDERED) {
                        return new CartAndMessage(cart, message);
                    } else {
                        LOG.debug("Cart {} is already ordered nothing to do.", cart.getId());
                        messageProcessedManager.setMessageIsProcessed(message);
                    }
                } else {
                    LOG.error("There is no cart connected to payment with id {}.", message.getResource().getId());
                    messageProcessedManager.setMessageIsProcessed(message);
                }
            } else {
                LOG.debug("PaymentTransactionCreatedOrUpdatedMessage {} has incorrect transaction state to be processed.", message.getId());
                messageProcessedManager.setMessageIsProcessed(message);
            }
        } else {
            LOG.error("There is no payment in commercetools platform with id {}.", message.getResource().getId());
            messageProcessedManager.setMessageIsProcessed(message);
        }

        // we tried to do all possible jobs. If CartAndMessage is not returned above -
        // don't try to process this message next time
        timeStampManager.setActualProcessedMessageTimeStamp(message.getLastModifiedAt());

        return null;
    }

    private Optional<Cart> getCorrespondingCart(final Payment payment) {
        final CartQuery cartQuery = CartQuery.of()
                .withPredicates(m -> m.paymentInfo().payments().isIn(Collections.singletonList(payment)));
        List<Cart> results = client.executeBlocking(cartQuery).getResults();
        if (results.isEmpty()) {
            return Optional.empty();
        } else {
            //assume one payment is not assigned to multiple carts
            assert results.size() == 1;
            return Optional.of(results.get(0));
        }
    }

    @Nullable
    private Payment getCorrespondingPayment(final PaymentTransactionCreatedOrUpdatedMessage message) {
        final String paymentId = message.getResource().getId();
        LOG.debug("Query CTP for Payment with ID {}", paymentId);
        final PaymentByIdGet paymentByIdGet = PaymentByIdGet.of(paymentId);
        return client.executeBlocking(paymentByIdGet);
    }
}
