package com.commercetools.paymenttoorderprocessor.jobs.actions;

import java.util.List;

import org.springframework.batch.item.ItemWriter;

import io.sphere.sdk.carts.Cart;

public class MessageWriter implements ItemWriter<Cart> {

    @Override
    public void write(List<? extends Cart> items) throws Exception {
        for (Cart item : items) {
            //TODO handle Message
        }
    }
}
