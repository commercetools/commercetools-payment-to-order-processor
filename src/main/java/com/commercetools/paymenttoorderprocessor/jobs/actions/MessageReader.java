package com.commercetools.paymenttoorderprocessor.jobs.actions;

import com.commercetools.paymenttoorderprocessor.customobjects.MessageProcessedManager;
import com.commercetools.paymenttoorderprocessor.dto.PaymentTransactionCreatedOrUpdatedMessage;
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;
import com.commercetools.paymenttoorderprocessor.utils.CtpQueryUtils;
import io.sphere.sdk.carts.Cart;
import io.sphere.sdk.carts.queries.CartQuery;
import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.client.SphereClient;
import io.sphere.sdk.messages.Message;
import io.sphere.sdk.messages.queries.MessageQuery;
import io.sphere.sdk.queries.PagedQueryResult;
import io.sphere.sdk.queries.QueryPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

/**
 * Reads PaymentTransactionStateChangedMessages from the commercetools platform.
 * To assure all messages are processed a Custom Object in the platform saves all message ids.
 *
 * @author mht@dotsource.de
 */

public class MessageReader implements ItemReader<PaymentTransactionCreatedOrUpdatedMessage> {

    private static final String PAYMENT_TRANSACTION_STATE_CHANGED = "PaymentTransactionStateChanged";
    private static final String PAYMENT_TRANSACTION_ADDED = "PaymentTransactionAdded";
    private boolean unprocessedMessagesQueueFilled = false;
    @Autowired
    private SphereClient client;
    @Autowired
    private TimeStampManager timeStampManager;
    @Autowired
    private MessageProcessedManager messageProcessedManager;
    @Value("${ctp.messages.processtransactionaddedmessages:true}")
    private Boolean processPaymentTransactionAddedMessages;
    @Value("${ctp.messages.processtransactionstatechangedmessages:true}")
    private Boolean processPaymentTransactionStateChangedMessages;
    @Nonnull
    private final Queue<PaymentTransactionCreatedOrUpdatedMessage> unprocessedMessagesQueue = new ArrayDeque<>();

    /**
     * @return the oldest unprocessed message from the queue, or <b>null</b> if no new messages to process in CTP.
     */
    @Override
    @Nullable
    public PaymentTransactionCreatedOrUpdatedMessage read() {
        if (!unprocessedMessagesQueueFilled) {
            MessageQuery query = buildQuery();
            Consumer<List<Message>> consumer = messages -> messages.stream()
                    .map(message -> message.as(PaymentTransactionCreatedOrUpdatedMessage.class))
                    .forEach(message -> {
                        if (messageProcessedManager.isMessageUnprocessed(message)) {
                            unprocessedMessagesQueue.add(message);
                        } else {
                            timeStampManager.setActualProcessedMessageTimeStamp(message.getLastModifiedAt());
                        }
                    });
            CtpQueryUtils.queryAll(client, query, consumer).thenApply(result -> unprocessedMessagesQueueFilled = true).toCompletableFuture().join();
        }
        return unprocessedMessagesQueue.poll();
    }


    //Due to eventual consistency messages could be created with a delay. Fetching several minutes prior last Timestamp
    private MessageQuery buildQuery() {

        MessageQuery messageQuery = MessageQuery.of();
        final ZonedDateTime timestamp = timeStampManager.getLastProcessedMessageTimeStamp();
        if (timestamp != null) {
            messageQuery = messageQuery.plusPredicates(
                    m -> m.lastModifiedAt().isGreaterThan(timestamp));
        }
        messageQuery = messageQuery.plusPredicates(m -> {
            QueryPredicate<Message> predicate = null;
            if (processPaymentTransactionAddedMessages) {
                predicate = m.type().is(PAYMENT_TRANSACTION_ADDED);
            }
            if (processPaymentTransactionStateChangedMessages) {
                if (predicate != null) {
                    predicate = predicate.or(m.type().is(PAYMENT_TRANSACTION_STATE_CHANGED));
                } else {
                    predicate = m.type().is(PAYMENT_TRANSACTION_STATE_CHANGED);
                }
            }
            return predicate;
        });

        return messageQuery;
    }
}
