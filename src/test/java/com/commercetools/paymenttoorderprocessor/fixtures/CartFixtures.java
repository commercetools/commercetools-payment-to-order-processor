package com.commercetools.paymenttoorderprocessor.fixtures;

import io.sphere.sdk.carts.Cart;
import io.sphere.sdk.carts.CartDraft;
import io.sphere.sdk.carts.commands.CartCreateCommand;
import io.sphere.sdk.client.BlockingSphereClient;

public class CartFixtures {
    public static Cart createCart(final BlockingSphereClient client, final CartDraft cartDraft) {
        return client.executeBlocking(CartCreateCommand.of(cartDraft));
    }
}
