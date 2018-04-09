package com.commercetools.paymenttoorderprocessor.jobs.actions;

import com.commercetools.paymenttoorderprocessor.customobjects.MessageProcessedManager;
import com.commercetools.paymenttoorderprocessor.testconfiguration.ExtendedTestConfiguration;
import com.commercetools.paymenttoorderprocessor.testconfiguration.HttpClientMockConfiguration;
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;
import com.commercetools.paymenttoorderprocessor.wrapper.CartAndMessage;
import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.json.SphereJsonUtils;
import io.sphere.sdk.payments.Payment;
import io.sphere.sdk.payments.messages.PaymentTransactionStateChangedMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(
        classes = {
                MessageFilterTest.TestConfig.class, HttpClientMockConfiguration.class, ExtendedTestConfiguration.class
        },
        initializers = ConfigFileApplicationContextInitializer.class)
// clean messageProcessedManager on every test
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class MessageFilterTest {

    @Configuration
    static class TestConfig {
        @Bean
        public BlockingSphereClient client() {
            return mock(BlockingSphereClient.class);
        }
    }

    @Autowired
    private MessageFilter messageProcessor;

    @Autowired
    private TimeStampManager timeStampManager;

    @Autowired
    private BlockingSphereClient client;

    @Autowired
    private MessageProcessedManager messageProcessedManager;

    private PaymentTransactionStateChangedMessage testMessage;

    @Before
    public void setUp() {
        testMessage = SphereJsonUtils.readObjectFromResource("mocks/messageFilter/PaymentTransactionStateChangedMessage.json", PaymentTransactionStateChangedMessage.class);
    }

    @Test
    public void messageProcessor_withEmptyPayment_updatesLastProcessedMessageTimeStamp() {
        assertThat(messageProcessedManager.isMessageUnprocessed(testMessage)).isTrue();
        final CartAndMessage cartAndMessage = messageProcessor.process(testMessage);
        assertThat(cartAndMessage).isNull();

        // when MessageFilter.process() returns null - timestamp is updated
        timeStampManager.persistLastProcessedMessageTimeStamp();
        assertThat(timeStampManager.getLastProcessedMessageTimeStamp()).isEqualTo(testMessage.getLastModifiedAt());
        assertThat(messageProcessedManager.isMessageUnprocessed(testMessage)).isFalse();
    }

    @Test
    public void messageProcessor_withExistentPayment_updatesLastProcessedMessageTimeStamp() {
        assertThat(messageProcessedManager.isMessageUnprocessed(testMessage)).isTrue();

        // return some payment on request
        when(client.executeBlocking(any())).thenReturn(mock(Payment.class));

        final CartAndMessage cartAndMessage = messageProcessor.process(testMessage);
        assertThat(cartAndMessage).isNull();

        // when MessageFilter.process() returns null - timestamp is updated
        timeStampManager.persistLastProcessedMessageTimeStamp();
        assertThat(timeStampManager.getLastProcessedMessageTimeStamp()).isEqualTo(testMessage.getLastModifiedAt());
        assertThat(messageProcessedManager.isMessageUnprocessed(testMessage)).isFalse();
    }
}