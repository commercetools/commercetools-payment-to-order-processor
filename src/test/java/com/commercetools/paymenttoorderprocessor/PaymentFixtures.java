package com.commercetools.paymenttoorderprocessor;

import java.util.function.UnaryOperator;

import javax.money.Monetary;
import javax.money.MonetaryAmount;

import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.payments.Payment;
import io.sphere.sdk.payments.PaymentDraft;
import io.sphere.sdk.payments.PaymentDraftBuilder;
import io.sphere.sdk.payments.commands.PaymentCreateCommand;
import io.sphere.sdk.payments.commands.PaymentDeleteCommand;
import io.sphere.sdk.utils.MoneyImpl;

public class PaymentFixtures {
    public static final MonetaryAmount EURO_20 = MoneyImpl.of(20, Monetary.getCurrency("EUR"));
    
    public static void withPayment(final BlockingSphereClient client, final UnaryOperator<PaymentDraftBuilder> builderMapping, final UnaryOperator<Payment> op) {
        final PaymentDraft paymentDraft = builderMapping.apply(PaymentDraftBuilder.of(EURO_20)).build();
        final Payment payment = client.executeBlocking(PaymentCreateCommand.of(paymentDraft));
        final Payment paymentToDelete = op.apply(payment);
        client.executeBlocking(PaymentDeleteCommand.of(paymentToDelete));
    }
    
    
    public static void withPayment(final BlockingSphereClient client, final UnaryOperator<Payment> op) {
        withPayment(client, a -> a, op);
    }
}
