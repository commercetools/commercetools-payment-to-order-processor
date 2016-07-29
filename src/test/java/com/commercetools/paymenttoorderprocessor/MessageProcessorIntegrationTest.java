package com.commercetools.paymenttoorderprocessor;

import static com.commercetools.paymenttoorderprocessor.fixtures.PaymentFixtures.EUR;
import static com.commercetools.paymenttoorderprocessor.fixtures.PaymentFixtures.EURO_20;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.experimental.theories.PotentialAssignment.CouldNotGenerateValueException;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.ConfigFileApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.commercetools.paymenttoorderprocessor.fixtures.CartFixtures;
import com.commercetools.paymenttoorderprocessor.fixtures.PaymentFixtures;
import com.commercetools.paymenttoorderprocessor.jobs.actions.MessageFilter;
import com.commercetools.paymenttoorderprocessor.jobs.actions.MessageReader;
import com.commercetools.paymenttoorderprocessor.testconfiguration.BasicTestConfiguration;
import com.commercetools.paymenttoorderprocessor.testconfiguration.ExtendedTestConfiguration;
import com.commercetools.paymenttoorderprocessor.testconfiguration.ReaderTestConfiguration1;
import com.neovisionaries.i18n.CountryCode;

import io.sphere.sdk.carts.Cart;
import io.sphere.sdk.carts.CartDraft;
import io.sphere.sdk.carts.CustomLineItemDraft;
import io.sphere.sdk.carts.commands.CartUpdateCommand;
import io.sphere.sdk.carts.commands.updateactions.AddPayment;
import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.models.Address;
import io.sphere.sdk.models.LocalizedString;
import io.sphere.sdk.payments.Payment;
import io.sphere.sdk.payments.Transaction;
import io.sphere.sdk.payments.TransactionDraft;
import io.sphere.sdk.payments.TransactionDraftBuilder;
import io.sphere.sdk.payments.TransactionState;
import io.sphere.sdk.payments.TransactionType;
import io.sphere.sdk.payments.commands.PaymentUpdateCommand;
import io.sphere.sdk.payments.commands.updateactions.AddTransaction;
import io.sphere.sdk.payments.commands.updateactions.ChangeTransactionState;
import io.sphere.sdk.payments.messages.PaymentTransactionStateChangedMessage;
import io.sphere.sdk.queries.PagedQueryResult;
import io.sphere.sdk.taxcategories.TaxCategory;
import io.sphere.sdk.taxcategories.queries.TaxCategoryQuery;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {BasicTestConfiguration.class, ExtendedTestConfiguration.class, ShereClientConfiguration.class, ReaderTestConfiguration1.class}, initializers = ConfigFileApplicationContextInitializer.class)
public class MessageProcessorIntegrationTest extends IntegrationTest {

    public static final Logger LOG = LoggerFactory.getLogger(MessageProcessorIntegrationTest.class);

    @Autowired
    private MessageReader messageReader;

    @Autowired
    private MessageFilter messageProcessor;
    
    @Autowired
    private BlockingSphereClient testClient;
    
    @Test
    public void messageProcesserIntegrationTest() throws Exception {
        LOG.debug("Starting Test createOrderIntegrationTest");
        PaymentFixtures.withPayment(testClient, payment -> {
            final PagedQueryResult<TaxCategory> result = testClient.executeBlocking(TaxCategoryQuery.of().byName("standard"));
            final List<TaxCategory> results = result.getResults();
            assertThat(results).isNotEmpty();
            final CustomLineItemDraft customLineItemDraft = CustomLineItemDraft.of(LocalizedString.ofEnglish("messageProcesserIntegrationTestCustomLineItem"), "Slug", EURO_20, results.get(0), 1L);
            final Address address = Address.of(CountryCode.DE);
            final Cart cart = CartFixtures.createCart(testClient, CartDraft.of(EUR)
                    .withCustomLineItems(Collections.singletonList(customLineItemDraft))
                    .withShippingAddress(address));
            final Cart cartWithPayment = testClient.executeBlocking(CartUpdateCommand.of(cart, AddPayment.of(payment)));
            
            final TransactionDraft transactionDraft = TransactionDraftBuilder.of(TransactionType.AUTHORIZATION, EURO_20).build();
            final AddTransaction addTransaction = AddTransaction.of(transactionDraft);
            
            final Payment paymentWithTransaction = testClient.executeBlocking(PaymentUpdateCommand.of(payment, addTransaction));
            assertThat(paymentWithTransaction.getTransactions().get(0).getState()).isEqualTo(TransactionState.PENDING);
            
            final Transaction transaction = paymentWithTransaction.getTransactions().get(0);
            final ChangeTransactionState changeTransactionState = ChangeTransactionState.of(TransactionState.SUCCESS, transaction.getId());
            final Payment paymentWithTransactionStateChange = testClient.executeBlocking(PaymentUpdateCommand.of(paymentWithTransaction, changeTransactionState));
            
            LOG.debug("Preparation done");
            assertEventually(() -> {
                PaymentTransactionStateChangedMessage message = messageReader.read();
                LOG.debug("Read message {}", message);
                assertThat(message).isNotNull();
                LOG.debug("Testing for equal {} {}", message.getResource().getId(), payment.getId());
                assertThat(message.getResource().getId()).isEqualTo(payment.getId());
                assertThat(message.getState()).isEqualTo(TransactionState.SUCCESS);
                LOG.debug("Message for Cart ProcessorTest OK");
                
                Cart cartToTest = messageProcessor.process(message).getCart();
                LOG.debug("Caught Cart {} from CTP", cartToTest);
                assertThat(cartToTest).isNotNull();
                assertThat(cartToTest.getId()).isEqualTo(cartWithPayment.getId());
            });
            return paymentWithTransactionStateChange;
        });
    }
}