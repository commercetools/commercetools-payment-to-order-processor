package com.commercetools.paymenttoorderprocessor.fixtures;

import com.commercetools.paymenttoorderprocessor.jobs.JobListener;
import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.client.ConcurrentModificationException;
import io.sphere.sdk.models.Versioned;
import io.sphere.sdk.models.errors.ConcurrentModificationError;
import io.sphere.sdk.payments.Payment;
import io.sphere.sdk.payments.PaymentDraft;
import io.sphere.sdk.payments.PaymentDraftBuilder;
import io.sphere.sdk.payments.commands.PaymentCreateCommand;
import io.sphere.sdk.payments.commands.PaymentDeleteCommand;
import io.sphere.sdk.utils.MoneyImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PaymentFixtures {
    public static final Logger LOG = LoggerFactory.getLogger(JobListener.class);

    public static final CurrencyUnit EUR = Monetary.getCurrency("EUR");
    public static final CurrencyUnit USD = Monetary.getCurrency("USD");
    public static final CurrencyUnit UAH = Monetary.getCurrency("UAH");

    public static final MonetaryAmount EUR_20 = MoneyImpl.of(20, EUR);
    public static final MonetaryAmount USD_30 = MoneyImpl.of(30, USD);
    public static final MonetaryAmount UAH_42 = MoneyImpl.of(42, UAH);

    public static void withPayment(final BlockingSphereClient client,
                                      final UnaryOperator<PaymentDraftBuilder> builderMapping,
                                      final UnaryOperator<Payment> op) {
        withPayments(1, client, builderMapping, payments -> {
            return Collections.singletonList(op.apply(payments.get(0)));
        });
    }

    public static void withPayments(final int numberOfPayments,
                                    final BlockingSphereClient client,
                                    final UnaryOperator<PaymentDraftBuilder> builderMapping,
                                    final UnaryOperator<List<Payment>> op) {
        List<Payment> payments = IntStream.range(0, 2).mapToObj(ignore -> {
            final PaymentDraft paymentDraft = builderMapping.apply(PaymentDraftBuilder.of(EUR_20)).build();
            return client.executeBlocking(PaymentCreateCommand.of(paymentDraft));
        }).collect(Collectors.toList());

        List<Payment> paymentsToDelete = op.apply(payments);

        for (Payment payment : paymentsToDelete) {
            deleteWith409Retry(client, payment.getId(), payment.getVersion());
        }
    }

    private static void deleteWith409Retry(final BlockingSphereClient client, final String id, Long version) {
        while(true) {
            try {
                client.executeBlocking(PaymentDeleteCommand.of(Versioned.of(id, version)));
                break;
            } catch (ConcurrentModificationException e) {
                version = e.getErrors().get(0).as(ConcurrentModificationError.class).getCurrentVersion();
            } catch (Exception e) {
                LOG.error("Exception while deleting payment[id='{}'] after test. Exception: {}", id, e.toString());
                // ignore the error, since this helper method should not fail the tests
                break;
            }
        }
    }

    public static void withPayments(int numberOfPayments, final BlockingSphereClient client, final UnaryOperator<List<Payment>> op) {
        withPayments(numberOfPayments, client, a -> a, op::apply);
    }

    public static void withPayment(final BlockingSphereClient client, final UnaryOperator<Payment> op) {
        withPayments(1, client, a -> a, payments -> {
            return Collections.singletonList(op.apply(payments.get(0)));
        });
    }
}
