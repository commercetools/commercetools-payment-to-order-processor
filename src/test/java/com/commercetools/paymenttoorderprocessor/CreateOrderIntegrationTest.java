package com.commercetools.paymenttoorderprocessor;

import static com.commercetools.paymenttoorderprocessor.PaymentFixtures.EURO_20;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Properties;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import com.commercetools.paymenttoorderprocessor.jobs.actions.MessageReader;
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;

import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.messages.Message;
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
@ContextConfiguration(loader=AnnotationConfigContextLoader.class)
public class CreateOrderIntegrationTest extends IntegrationTest {

    @Configuration
    static class ContextConfiguration {
        @Bean 
        public BlockingSphereClient client() {
            return testClient();
        }
        @Bean
        public TimeStampManager timeStampManager() {
            return new TimeStampManager() {
                
                @Override
                public void setActualProcessedMessageTimeStamp(ZonedDateTime timeStamp) {
                    //not needed in test
                }
                
                @Override
                public void persistLastProcessedMessageTimeStamp() {
                    //not needed in test
                }
                
                @Override
                public Optional<ZonedDateTime> getLastProcessedMessageTimeStamp() {
                    return Optional.of(ZonedDateTime.now().minusMinutes(1L));
                }
            };
        }
        @Bean MessageReader messageReader() {
            return new MessageReader();
        }
        
        @Bean
        public static PropertySourcesPlaceholderConfigurer properties() throws Exception {
            final PropertySourcesPlaceholderConfigurer pspc = new PropertySourcesPlaceholderConfigurer();
            Properties properties = new Properties();
            properties.setProperty("ctp.poller.messagetype", "PaymentTransactionStateChanged");

            pspc.setProperties(properties);
            return pspc;
        }
    }
    
    @Autowired
    private MessageReader messageReader;
    
    
    @Test
    public void createOrderIntegrationTest()  throws Exception {
        PaymentFixtures.withPayment(testClient(), payment-> {
            final TransactionDraft transactionDraft = TransactionDraftBuilder.of(TransactionType.AUTHORIZATION, EURO_20).build();
            final AddTransaction addTransaction = AddTransaction.of(transactionDraft);
            final Payment paymentWithTransaction = testClient().executeBlocking(PaymentUpdateCommand.of(payment, addTransaction));
            assertThat(paymentWithTransaction.getTransactions().get(0).getState()).isEqualTo(TransactionState.PENDING);
            
            final Transaction transaction = paymentWithTransaction.getTransactions().get(0);
            final ChangeTransactionState changeTransactionState = ChangeTransactionState.of(TransactionState.SUCCESS, transaction.getId());
            final Payment paymentWithTransactionStateChange = testClient().executeBlocking(PaymentUpdateCommand.of(paymentWithTransaction, changeTransactionState));
            
            
            assertEventually(() -> {
                try {
                    Message message = messageReader.read();
                    assertThat(message.getResource().getId()).isEqualTo(payment.getId());
                    assertThat(message.getType()).isEqualTo("PaymentTransactionStateChanged");
                    assertThat(message.as(PaymentTransactionStateChangedMessage.class).getState()).isEqualTo(TransactionState.SUCCESS);
                }
                catch (Exception e){
                }
            });
            return paymentWithTransactionStateChange;
        });
    }
}