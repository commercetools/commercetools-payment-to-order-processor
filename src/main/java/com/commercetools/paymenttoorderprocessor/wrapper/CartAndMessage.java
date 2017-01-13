package com.commercetools.paymenttoorderprocessor.wrapper;

import io.sphere.sdk.carts.Cart;
import io.sphere.sdk.messages.Message;

/**
 * Wrapper for Cart and Messages
 *
 * @author mht@dotsource.de
 */
public class CartAndMessage {
    final private Cart cart;
    final private Message message;

    public CartAndMessage(final Cart cart, final Message message) {
        this.cart = cart;
        this.message = message;
    }

    public Cart getCart() {
        return cart;
    }

    public Message getMessage() {
        return message;
    }
}
