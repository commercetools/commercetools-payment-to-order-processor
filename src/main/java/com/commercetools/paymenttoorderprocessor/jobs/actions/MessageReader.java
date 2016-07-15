package com.commercetools.paymenttoorderprocessor.jobs.actions;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.commercetools.paymenttoorderprocessor.timestamp.TimeStamp;

import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.customobjects.CustomObject;
import io.sphere.sdk.customobjects.CustomObjectDraft;
import io.sphere.sdk.customobjects.commands.CustomObjectUpsertCommand;
import io.sphere.sdk.customobjects.queries.CustomObjectQuery;
import io.sphere.sdk.messages.Message;
import io.sphere.sdk.messages.queries.MessageQuery;
import io.sphere.sdk.queries.PagedQueryResult;

public class MessageReader implements ItemReader<Message> {

    public static final Logger LOG = LoggerFactory.getLogger(MessageReader.class);
    
    private final String SERVICENAME = "commercetools-payment-to-order-processor";
    private final String KEY = "lastUpdated";
    @Autowired
    private BlockingSphereClient client;
    
    @Value("${ctp.poller.messagetype}")
    private String messageType;

    private List<Message> messages = Collections.emptyList();
    private boolean wasTimeStampQueried = false;
    private boolean wasInitialQueried = false;
    private long total;
    private long offset = 0;

    private MessageQuery messageQuery;
    private Optional<CustomObject<TimeStamp>> lastTimestamp = Optional.empty();
    private ZonedDateTime timeOfProcessedMessage;
    
    @Override
    public Message read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        if(!wasTimeStampQueried) {
            getLastProcessedMessageTimeStamp();
        }
        if(isQueryNeeded()) {
            queryPlatform();
        }
        return getMessageFromList();
    }

    /***
     * Querys Custom-Object endpoint
     */
    private void getLastProcessedMessageTimeStamp() {
        final CustomObjectQuery<TimeStamp> customObjectQuery = CustomObjectQuery.of(TimeStamp.class)
                .byContainer(SERVICENAME);
        final PagedQueryResult<CustomObject<TimeStamp>> result = client.executeBlocking(customObjectQuery);
        final List<CustomObject<TimeStamp>> results = result.getResults();
        if (results.isEmpty()) {
            LOG.warn("No LastProcessedMessage was found");
        }
        else {
            lastTimestamp = Optional.of(results.get(0));
        }
        wasTimeStampQueried = true;
    }
    
    private void setLastProcessedMessageTimeStamp() {
        final CustomObjectDraft<TimeStamp> draft = createCustomObjectDraft();
        final CustomObjectUpsertCommand<TimeStamp> updateCommad = CustomObjectUpsertCommand.of(draft);
        client.executeBlocking(updateCommad);
    }

    private CustomObjectDraft<TimeStamp> createCustomObjectDraft() {
        final TimeStamp timeStamp = new TimeStamp(timeOfProcessedMessage);
        LOG.info("Writing Custom Object ".concat(timeOfProcessedMessage.toString()));
        if (lastTimestamp.isPresent()) {
            return CustomObjectDraft.ofVersionedUpdate(lastTimestamp.get(), timeStamp, TimeStamp.class);
        }
        else {
            return CustomObjectDraft.ofUnversionedUpsert(SERVICENAME, KEY ,timeStamp, TimeStamp.class);
        }
    }

    private Message getMessageFromList() {
        if (messages.isEmpty()){
            setLastProcessedMessageTimeStamp();
            return null;
        }
        else{
            timeOfProcessedMessage = messages.get(0).getLastModifiedAt();
            return messages.remove(0);
        }
    }

    private boolean isQueryNeeded() {
        if (!wasInitialQueried) {
            return true;
        }
        
        return (messages.isEmpty() && total > offset);
    }

    
    private void queryPlatform(){
        buildQuery();
        final PagedQueryResult<Message> result = client.executeBlocking(messageQuery);
        total = result.getTotal();
        offset = result.getOffset() + result.getCount();
        messages = result.getResults();
    }
    
    private void buildQuery(){
        messageQuery = MessageQuery.of()
                .withPredicates(m -> m.type().is(messageType))
                .withSort(m -> m.lastModifiedAt().sort().asc())
                .withOffset(offset)
                .withLimit(500);
        if (lastTimestamp.isPresent()) {
            messageQuery = messageQuery
                    .plusPredicates(m -> m.lastModifiedAt().isGreaterThan(lastTimestamp.get().getValue().getLastTimeStamp().minusMinutes(2)));
        }
    }
}
