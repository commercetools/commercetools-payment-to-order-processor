package com.commercetools.paymenttoorderprocessor.jobs.actions;

import com.commercetools.paymenttoorderprocessor.customobjects.MessageProcessedManager;
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;
import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.messages.Message;
import io.sphere.sdk.messages.queries.MessageQuery;
import io.sphere.sdk.payments.messages.PaymentTransactionStateChangedMessage;
import io.sphere.sdk.queries.PagedQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Reads PaymentTransactionStateChangedMessages from the commercetools platform.
 * To assure all messages are processed a Custom Object in the platform saves all message ids.
 *
 * @author mht@dotsource.de
 */

public class MessageReader implements ItemReader<PaymentTransactionStateChangedMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(MessageReader.class);

    @Autowired
    private BlockingSphereClient client;

    @Autowired
    private TimeStampManager timeStampManager;

    @Autowired
    private MessageProcessedManager messageProcessedManager;

    private static final String MESSAGETYPE = "PaymentTransactionStateChanged";

    @Value("${ctp.messagereader.minutesoverlapping}")
    private Integer minutesOverlapping;

    @Nonnull
    private Queue<PaymentTransactionStateChangedMessage> unprocessedMessagesQueue = new ArrayDeque<>();

    private boolean wasInitialQueried = false;
    private long total;
    private long offset = 0L;
    final int RESULTSPERPAGE = 500;
    final int PAGEOVERLAP = 5;

    /**
     * @return the oldest unprocessed message from the queue, or <b>null</b> if no new messages to process in CTP.
     */
    @Override
    @Nullable
    public PaymentTransactionStateChangedMessage read() {
        LOG.debug("wasInitialQueried: {}", wasInitialQueried);

        while (isQueryNeeded()) {
            fetchUnprocessedMessagesFromPlatform();
        }

        return getUnprocessedMessageFromQueue();
    }

    /**
     * @return oldest unprocessed message from the queue if exists, or <b>null</b>
     */
    @Nullable
    private PaymentTransactionStateChangedMessage getUnprocessedMessageFromQueue() {
        return unprocessedMessagesQueue.poll();
    }

    /**
     * A first or next CTP query is needed if we don't have unprocessed messages in the queue.
     *
     * @return <b>true</b> if messages queue never fetched or {@code unprocessedMessagesQueue} is empty and CTP still
     * has items to fetch (query next page).
     */
    private boolean isQueryNeeded() {
        return !wasInitialQueried || (unprocessedMessagesQueue.isEmpty() && total > offset);
    }

    /**
     * Fetch messages from the platform and put all unprocessed messages to {@code unprocessedMessagesQueue}.
     * Also, update {@link TimeStampManager#setActualProcessedMessageTimeStamp(java.time.ZonedDateTime)
     * actualProcessedMessageTimeStamp} for processed messages.
     */
    private void fetchUnprocessedMessagesFromPlatform() {
        final PagedQueryResult<Message> result = queryPlatform();
        result.getResults().stream()
                .map(message -> message.as(PaymentTransactionStateChangedMessage.class))
                .forEach(message -> {
                    if (messageProcessedManager.isMessageUnprocessed(message)) {
                        unprocessedMessagesQueue.add(message);
                    } else {
                        timeStampManager.setActualProcessedMessageTimeStamp(message.getLastModifiedAt());
                    }
                });

        LOG.info("fetched messages [{}-{}] of total {}, {} of them are are unprocessed",
                result.getOffset() + 1, result.getOffset() + result.getCount(), result.getTotal(), unprocessedMessagesQueue.size());
    }

    private PagedQueryResult<Message> queryPlatform() {
        LOG.debug("Query CTP for Messages");
        final MessageQuery messageQuery = buildQuery();
        final PagedQueryResult<Message> result = client.executeBlocking(messageQuery);
        //Get the total workload from first Query
        if (!wasInitialQueried) {
            total = result.getTotal();
            LOG.debug("First Query returned {} results.", total);
        }
        //Due to nondeterministic ordering of messages with same timestamp we fetch next pages with overlap
        offset = result.getOffset() + RESULTSPERPAGE - PAGEOVERLAP;
        wasInitialQueried = true;
        return result;
    }


    //Due to eventual consistency messages could be created with a delay. Fetching several minutes prior last Timestamp
    private MessageQuery buildQuery() {

        MessageQuery messageQuery = MessageQuery.of()
                .withPredicates(m -> m.type().is(MESSAGETYPE))
                .withSort(m -> m.lastModifiedAt().sort().asc())
                .withOffset(offset)
                .withLimit(RESULTSPERPAGE);

        final ZonedDateTime timestamp = timeStampManager.getLastProcessedMessageTimeStamp();

        if (timestamp != null) {
            messageQuery = messageQuery.plusPredicates(
                    m -> m.lastModifiedAt().isGreaterThan(timestamp.minusMinutes(minutesOverlapping)));
        }

        return messageQuery;
    }
}
