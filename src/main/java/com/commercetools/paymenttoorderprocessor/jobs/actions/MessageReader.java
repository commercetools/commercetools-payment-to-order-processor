package com.commercetools.paymenttoorderprocessor.jobs.actions;

import java.util.Collections;
import java.util.List;

import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.messages.Message;
import io.sphere.sdk.messages.queries.MessageQuery;
import io.sphere.sdk.queries.PagedQueryResult;

public class MessageReader implements ItemReader<Message> {

    @Autowired
    private BlockingSphereClient client;
    
    @Value("${ctp.poller.messagetype}")
    private String messageType;

    private List<Message> messages = Collections.emptyList();
    private boolean wasInitialQueried = false;
    private long total;
    private long offset = 0;

    @Override
    public Message read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
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
        final MessageQuery messageQuery = MessageQuery.of()
                .withPredicates(m -> m.type().is(messageType))
                .withSort(m -> m.createdAt().sort().asc())
                .withOffset(offset)
                .withLimit(500);
        final PagedQueryResult<Message> result = client.executeBlocking(messageQuery);
        total = result.getTotal();
        offset = result.getOffset() + result.getCount();
        messages = result.getResults();
        wasInitialQueried = true;
    }
}
