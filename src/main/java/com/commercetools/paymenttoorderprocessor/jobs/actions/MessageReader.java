package com.commercetools.paymenttoorderprocessor.jobs.actions;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;

import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.messages.Message;
import io.sphere.sdk.messages.queries.MessageQuery;
import io.sphere.sdk.payments.messages.PaymentTransactionStateChangedMessage;
import io.sphere.sdk.queries.PagedQueryResult;

public class MessageReader implements ItemReader<Message> {

    public static final Logger LOG = LoggerFactory.getLogger(MessageReader.class);
    
    @Autowired
    private BlockingSphereClient client;
    
    @Autowired
    private TimeStampManager timeStampManager;
    
    @Value("${ctp.poller.messagetype}")
    private String messageType;

    private List<Message> messages = Collections.emptyList();
    private boolean wasInitialQueried = false;
    private long total;
    private long offset = 0;

    private MessageQuery messageQuery;
    
    @Override
    public Message read() {
        if(isQueryNeeded()) {
            queryPlatform();
        }
        return getMessageFromList();
    }

    private Message getMessageFromList() {
        if (messages.isEmpty()){
            return null;
        }
        else{
            timeStampManager.setActualProcessedMessageTimeStamp(messages.get(0).getLastModifiedAt());
            return messages.remove(0).as(PaymentTransactionStateChangedMessage.class);
        }
    }

    private boolean isQueryNeeded() {
        if (!wasInitialQueried) {
            return true;
        }
        
        return (messages.isEmpty() && total > offset);
    }

    
    private void queryPlatform(){
        LOG.info("Query CTP for Messages");
        buildQuery();
        final PagedQueryResult<Message> result = client.executeBlocking(messageQuery);
        total = result.getTotal();
        offset = result.getOffset() + result.getCount();
        messages = result.getResults();
        wasInitialQueried = true;
    }
    
    private void buildQuery(){
        messageQuery = MessageQuery.of()
                .withPredicates(m -> m.type().is(messageType))
                .withSort(m -> m.lastModifiedAt().sort().asc())
                .withOffset(offset)
                .withLimit(500);
        final Optional<ZonedDateTime> timestamp = timeStampManager.getLastProcessedMessageTimeStamp();
        if (timestamp.isPresent()) {
            messageQuery = messageQuery
                    .plusPredicates(m -> m.lastModifiedAt().isGreaterThan(timestamp.get().minusMinutes(2)));
        }
    }
}
