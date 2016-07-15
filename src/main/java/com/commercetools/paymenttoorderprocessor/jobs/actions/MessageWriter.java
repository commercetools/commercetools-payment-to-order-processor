package com.commercetools.paymenttoorderprocessor.jobs.actions;

import java.util.List;

import org.springframework.batch.item.ItemWriter;

import io.sphere.sdk.messages.Message;

public class MessageWriter implements ItemWriter<Message> {

    @Override
    public void write(List<? extends Message> items) throws Exception {
        for (Message item : items) {
            //TODO handle Message
        }
    }
}
