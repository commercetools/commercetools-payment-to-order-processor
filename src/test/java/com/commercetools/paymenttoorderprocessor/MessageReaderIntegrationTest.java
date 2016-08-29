package com.commercetools.paymenttoorderprocessor;

import static com.commercetools.paymenttoorderprocessor.fixtures.PaymentFixtures.EURO_20;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.ConfigFileApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.commercetools.paymenttoorderprocessor.fixtures.PaymentFixtures;
import com.commercetools.paymenttoorderprocessor.jobs.actions.MessageReader;
import com.commercetools.paymenttoorderprocessor.testconfiguration.BasicTestConfiguration;
import com.commercetools.paymenttoorderprocessor.testconfiguration.ExtendedTestConfiguration;

import io.sphere.sdk.client.BlockingSphereClient;
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

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {BasicTestConfiguration.class, ExtendedTestConfiguration.class, ShereClientConfiguration.class, MessageReaderIntegrationTest.ContextConfiguration.class}, initializers = ConfigFileApplicationContextInitializer.class)
public class MessageReaderIntegrationTest extends IntegrationTest {

    //For each test we need own instance of messageReader because its not stateless
    @Configuration
    public static class ContextConfiguration {
        @Bean
        public MessageReader messageReader() {
            return new MessageReader();
        }
    }

    @Autowired
    private BlockingSphereClient testClient;


    @Autowired
    private MessageReader messageReader;

    @Test
    public void messageReaderIntegrationTest() throws Exception {
        PaymentFixtures.withPayment(testClient, payment-> {

            //Preconditions create message in commercetoolsplatform:
            final TransactionDraft transactionDraft = TransactionDraftBuilder.of(TransactionType.AUTHORIZATION, EURO_20).build();
            final AddTransaction addTransaction = AddTransaction.of(transactionDraft);
            final Payment paymentWithTransaction = testClient.executeBlocking(PaymentUpdateCommand.of(payment, addTransaction));
            assertThat(paymentWithTransaction.getTransactions().get(0).getState()).isEqualTo(TransactionState.PENDING);
            
            final Transaction transaction = paymentWithTransaction.getTransactions().get(0);
            final ChangeTransactionState changeTransactionState = ChangeTransactionState.of(TransactionState.SUCCESS, transaction.getId());
            final Payment paymentWithTransactionStateChange = testClient.executeBlocking(PaymentUpdateCommand.of(paymentWithTransaction, changeTransactionState));

            //Check if the message will be read:
            assertEventually(() -> {
                PaymentTransactionStateChangedMessage message = messageReader.read();
                assertThat(message).isNotNull();
                assertThat(message.getResource().getId()).isEqualTo(payment.getId());
                assertThat(message.getState()).isEqualTo(TransactionState.SUCCESS);
            });
            return paymentWithTransactionStateChange;
        });
    }
}