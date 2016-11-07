package com.commercetools.paymenttoorderprocessor.jobs.actions;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.commercetools.paymenttoorderprocessor.customobjects.MessageProcessedManager;
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;

import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.messages.Message;
import io.sphere.sdk.messages.queries.MessageQuery;
import io.sphere.sdk.payments.messages.PaymentTransactionStateChangedMessage;
import io.sphere.sdk.queries.PagedQueryResult;

/**
 * Reads PaymentTransactionStateChangedMessages from the commercetools platform.
 * To assure all messages are processed a Custom Object in the platform saves all message ids.
 * @author mht@dotsource.de
 *
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

    private List<PaymentTransactionStateChangedMessage> messages = Collections.emptyList();
    private boolean wasInitialQueried = false;
    private long total;
    private long offset = 0L;
    private final int RESULTSPERPAGE = 500;
    private final int PAGEOVERLAP = 5;

    private MessageQuery messageQuery;

    @Override
    public PaymentTransactionStateChangedMessage read() {
        LOG.debug("wasInitialQueried: {}", wasInitialQueried);
        if (isQueryNeeded()) {
            getUnprocessedMessagesFromPlatform();
        }
        return getMessageFromList();
    }

    private PaymentTransactionStateChangedMessage getMessageFromList() {
        if (messages.isEmpty()) {
            return null;
        } else {
            timeStampManager.setActualProcessedMessageTimeStamp(messages.get(0).getLastModifiedAt());
            return messages.remove(0);
        }
    }

    private boolean isQueryNeeded() {
        return !wasInitialQueried || (messages.isEmpty() && total > offset);
    }

    private void getUnprocessedMessagesFromPlatform() {
        final List<Message> result = queryPlatform();
        messages =  result.stream()
                .map(message -> message.as(PaymentTransactionStateChangedMessage.class))
                .filter(message -> messageProcessedManager.isMessageUnprocessed(message))
                .collect(Collectors.toList());

        LOG.info("{} of {} messages are unprocessed", messages.size(), result.size());
    }


    private List<Message> queryPlatform() {
        LOG.info("Query CTP for Messages");
        buildQuery();
        final PagedQueryResult<Message> result = client.executeBlocking(messageQuery);
        //Get the total workload from first Query
        if (!wasInitialQueried) {
            total = result.getTotal();
            LOG.info("First Query returned {} results. This this the workload for the Job.", total);
        }
        //Due to nondeterministic ordering of messages with same timestamp we fetch next pages with overlap
        offset = result.getOffset() + RESULTSPERPAGE - PAGEOVERLAP;
        wasInitialQueried = true;
        return result.getResults();
    }


    //Due to eventual consistency messages could be created with a delay. Fetching several minutes prior last Timestamp
    private void buildQuery() {
        messageQuery = MessageQuery.of()
                .withPredicates(m -> m.type().is(MESSAGETYPE))
                .withSort(m -> m.lastModifiedAt().sort().asc())
                .withOffset(offset)
                .withLimit(RESULTSPERPAGE);
        final Optional<ZonedDateTime> timestamp = timeStampManager.getLastProcessedMessageTimeStamp();
        if (timestamp.isPresent()) {
            messageQuery = messageQuery.plusPredicates(
                    m -> m.lastModifiedAt().isGreaterThan(timestamp.get().minusMinutes(minutesOverlapping)));
        }
    }
}
