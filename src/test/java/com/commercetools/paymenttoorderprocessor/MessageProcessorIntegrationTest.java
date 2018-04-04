package com.commercetools.paymenttoorderprocessor;

import com.commercetools.paymenttoorderprocessor.fixtures.CartFixtures;
import com.commercetools.paymenttoorderprocessor.fixtures.PaymentFixtures;
import com.commercetools.paymenttoorderprocessor.jobs.actions.MessageFilter;
import com.commercetools.paymenttoorderprocessor.jobs.actions.MessageReader;
import com.commercetools.paymenttoorderprocessor.testconfiguration.BasicTestConfiguration;
import com.commercetools.paymenttoorderprocessor.testconfiguration.ExtendedTestConfiguration;
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
import io.sphere.sdk.payments.messages.PaymentTransactionStateChangedMessage;
import io.sphere.sdk.queries.PagedQueryResult;
import io.sphere.sdk.taxcategories.TaxCategory;
import io.sphere.sdk.taxcategories.queries.TaxCategoryQuery;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collections;
import java.util.List;

import static com.commercetools.paymenttoorderprocessor.fixtures.PaymentFixtures.EUR;
import static com.commercetools.paymenttoorderprocessor.fixtures.PaymentFixtures.EUR_20;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {BasicTestConfiguration.class, ExtendedTestConfiguration.class,
        ShereClientConfiguration.class},
        initializers = ConfigFileApplicationContextInitializer.class,
        loader = SpringBootContextLoader.class)
public class MessageProcessorIntegrationTest extends IntegrationTest {

    @Autowired
    private MessageReader messageReader;

    @Autowired
    private MessageFilter messageProcessor;

    @Autowired
    private BlockingSphereClient testClient;

    @Test
    public void messageProcessorSuccess() throws Exception {
        PaymentFixtures.withPayment(testClient, payment -> {

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
            final PaymentTransactionStateChangedMessage[] message = new PaymentTransactionStateChangedMessage[1];

            //Give Platform time to create messages
            try {
                Thread.sleep(10000L);
            } catch (InterruptedException e) {
            }
            //get the correct message to read
            assertEventually(() -> {
                message[0] = messageReader.read();
                assertThat(message[0]).isNotNull();
                assertThat(message[0].getResource().getId()).isEqualTo(payment.getId());
            });

            //test if message is processed correctly (i.e. -> get the corresponding CardAndMessage-Wrapper)
            final CartAndMessage cartAndMessage = messageProcessor.process(message[0]);
            assertThat(cartAndMessage).isNotNull();
            final Cart cartToTest = cartAndMessage.getCart();
            assertThat(cartToTest).isNotNull();
            assertThat(cartToTest.getId()).isEqualTo(cartWithPayment.getId());

            return paymentWithTransactionStateChange;
        });
    }
}