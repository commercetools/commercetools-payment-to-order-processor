package com.commercetools.paymenttoorderprocessor.fixtures;

import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.payments.Payment;
import io.sphere.sdk.payments.PaymentDraft;
import io.sphere.sdk.payments.PaymentDraftBuilder;
import io.sphere.sdk.payments.commands.PaymentCreateCommand;
import io.sphere.sdk.utils.MoneyImpl;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import java.util.function.UnaryOperator;

public class PaymentFixtures {
    public static final CurrencyUnit EUR = Monetary.getCurrency("EUR");
    public static final CurrencyUnit USD = Monetary.getCurrency("USD");
    public static final CurrencyUnit UAH = Monetary.getCurrency("UAH");

    public static final MonetaryAmount EUR_20 = MoneyImpl.of(20, EUR);
    public static final MonetaryAmount USD_30 = MoneyImpl.of(30, USD);
    public static final MonetaryAmount UAH_42 = MoneyImpl.of(42, UAH);

    public static Payment withPayment(final BlockingSphereClient client,
                                   final UnaryOperator<PaymentDraftBuilder> builderMapping,
                                   final UnaryOperator<Payment> op) {
        final PaymentDraft paymentDraft = builderMapping.apply(PaymentDraftBuilder.of(EUR_20)).build();
        final Payment payment = client.executeBlocking(PaymentCreateCommand.of(paymentDraft));
        final Payment paymentToDelete = op.apply(payment);

        return paymentToDelete;
    }

    public static void withPayment(final BlockingSphereClient client, final UnaryOperator<Payment> op) {
        withPayment(client, a -> a, op);
    }
}
