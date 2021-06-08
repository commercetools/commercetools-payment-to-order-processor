package com.commercetools.paymenttoorderprocessor.helper;

import com.commercetools.paymenttoorderprocessor.dto.PaymentTransactionCreatedOrUpdatedMessage;
import com.commercetools.paymenttoorderprocessor.fixtures.CartFixtures;
import com.commercetools.paymenttoorderprocessor.jobs.actions.MessageFilter;
import com.commercetools.paymenttoorderprocessor.jobs.actions.MessageReader;
import com.commercetools.paymenttoorderprocessor.wrapper.CartAndMessage;
import com.neovisionaries.i18n.CountryCode;
import io.sphere.sdk.carts.Cart;
import io.sphere.sdk.carts.CartDraft;
import io.sphere.sdk.carts.CustomLineItemDraft;
import io.sphere.sdk.carts.commands.CartUpdateCommand;
import io.sphere.sdk.carts.commands.updateactions.AddPayment;
import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.models.Address;
import io.sphere.sdk.models.LocalizedString;
import io.sphere.sdk.payments.*;
import io.sphere.sdk.payments.commands.PaymentUpdateCommand;
import io.sphere.sdk.payments.commands.updateactions.AddTransaction;
import io.sphere.sdk.payments.commands.updateactions.ChangeTransactionState;
import io.sphere.sdk.queries.PagedQueryResult;
import io.sphere.sdk.taxcategories.TaxCategory;
import io.sphere.sdk.taxcategories.queries.TaxCategoryQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.Collections;
import java.util.List;

import static com.commercetools.paymenttoorderprocessor.IntegrationTest.assertEventually;
import static com.commercetools.paymenttoorderprocessor.fixtures.PaymentFixtures.EUR;
import static com.commercetools.paymenttoorderprocessor.fixtures.PaymentFixtures.EUR_20;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

public class CartAndMessageCreateHelper {

    @Autowired
    BlockingSphereClient testClient;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MessageFilter messageProcessor;

    /**
     * Create a cart and attach the {@code payment} to it, set successful payment change transaction for the
     * {@code payment} and wait after related message is created.
     *
     * At the end - fetch the message and return {@link CartAndMessage} object with these values.
     *
     * The operation is blocking.
     *
     * @param payment payment to process
     * @return {@link CartAndMessage} with created cart and successful payment state change message.
     */
    public  CartAndMessage createCartAndMessage(Payment payment) {
        //Preconditions: to create message in commercetools platform
        final PagedQueryResult<TaxCategory> result = testClient.executeBlocking(TaxCategoryQuery.of().byName("standard"));
        final List<TaxCategory> results = result.getResults();
        assertThat(results).isNotEmpty();
        final CustomLineItemDraft customLineItemDraft = CustomLineItemDraft.of(LocalizedString.ofEnglish("messageProcesserIntegrationTestCustomLineItem"), "Slug", EUR_20, results.get(0), 1L);
        final Address address = Address.of(CountryCode.DE);
        final Cart cart = CartFixtures.createCart(testClient, CartDraft.of(EUR)
                .withCustomLineItems(Collections.singletonList(customLineItemDraft))
                .withShippingAddress(address));
        final Cart cartWithPayment = testClient.executeBlocking(CartUpdateCommand.of(cart, AddPayment.of(payment)));

        final TransactionDraft transactionDraft = TransactionDraftBuilder.of(TransactionType.AUTHORIZATION, EUR_20).state(TransactionState.INITIAL).build();
        final AddTransaction addTransaction = AddTransaction.of(transactionDraft);

        final Payment paymentWithTransaction = testClient.executeBlocking(PaymentUpdateCommand.of(payment, addTransaction));
        assertThat(paymentWithTransaction.getTransactions().get(0).getState()).isEqualTo(TransactionState.INITIAL);

        final Transaction transaction = paymentWithTransaction.getTransactions().get(0);
        final ChangeTransactionState changeTransactionState = ChangeTransactionState.of(TransactionState.SUCCESS, transaction.getId());
        final Payment paymentWithTransactionStateChange = testClient.executeBlocking(PaymentUpdateCommand.of(paymentWithTransaction, changeTransactionState));

        //final array so lambda can use it
        final CartAndMessage[] cartAndMessage = new CartAndMessage[1];

        //Give Platform time to create messages
        try {
            Thread.sleep(10000L);
        } catch (InterruptedException e) {
        }

        // because MessageReader is stateful we have to fetch a new instance created payment message
        MessageReader messageReader = context.getBean(MessageReader.class);

        //get the correct message to read and return correct cartAndMessage
        assertEventually(() -> {
            PaymentTransactionCreatedOrUpdatedMessage message = messageReader.read();
            assertThat(message).isNotNull();
            assertThat(message.getResource().getId()).isEqualTo(payment.getId());
            //test if message is processed correctly (i.e. -> get the corresponding CardAndMessage-Wrapper)
            final CartAndMessage cartAndMessageTemp = messageProcessor.process(message);
            assertThat(cartAndMessageTemp).isNotNull()
                    .withFailMessage(format("Can't create message CartAndMessage for the payment [%s]", payment));
            final Cart cartToTest = cartAndMessageTemp.getCart();
            assertThat(cartToTest).isNotNull();
            assertThat(cartToTest.getId()).isEqualTo(cartWithPayment.getId())
                    .withFailMessage(format("Processed cart id [%s] is incorrect", cartToTest.getId()));

            cartAndMessage[0] = cartAndMessageTemp;
        });

        assertThat(cartAndMessage[0]).isNotNull();

        return cartAndMessage[0];
    }
}
