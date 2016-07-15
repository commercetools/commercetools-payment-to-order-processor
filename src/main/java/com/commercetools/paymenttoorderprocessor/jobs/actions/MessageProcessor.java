package com.commercetools.paymenttoorderprocessor.jobs.actions;

import org.springframework.batch.item.ItemProcessor;

import io.sphere.sdk.messages.Message;


public class MessageProcessor implements ItemProcessor<Message, Message> {
    @Override
    public Message process(Message message) throws Exception {
        //TODO Filter Messages
        return message;
    }

}
