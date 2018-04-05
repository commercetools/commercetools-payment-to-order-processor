package com.commercetools.paymenttoorderprocessor.jobs.actions;

import com.commercetools.paymenttoorderprocessor.customobjects.MessageProcessedManager;
import com.commercetools.paymenttoorderprocessor.testconfiguration.ExtendedTestConfiguration;
import com.commercetools.paymenttoorderprocessor.testconfiguration.HttpClientMockConfiguration;
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;
import com.fasterxml.jackson.core.type.TypeReference;
import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.json.SphereJsonUtils;
import io.sphere.sdk.messages.queries.MessageQuery;
import io.sphere.sdk.payments.messages.PaymentTransactionStateChangedMessage;
import io.sphere.sdk.queries.PagedQueryResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(
        classes = {
                MessageReaderTest.TestConfig.class, HttpClientMockConfiguration.class, ExtendedTestConfiguration.class
        },
        initializers = ConfigFileApplicationContextInitializer.class)
// clean MessageReader and messageProcessedManager on every test
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class MessageReaderTest {

    @Configuration
    static class TestConfig {

        @Bean
        public BlockingSphereClient client() {
            return mock(BlockingSphereClient.class);
        }
    }

    private static final String MOCKS = "mocks/messageReader/";

    private static final TypeReference<PagedQueryResult<PaymentTransactionStateChangedMessage>> PagedQueryWithMessages =
            new TypeReference<PagedQueryResult<PaymentTransactionStateChangedMessage>>() {
            };

    @Autowired
    private MessageReader messageReader;

    @Autowired
    private TimeStampManager timeStampManager;

    @Autowired
    private MessageProcessedManager messageProcessedManager;

    @Autowired
    private BlockingSphereClient client;

    @Test
    public void read_whenEmpty_returnsNull() {
        when(client.executeBlocking(any())).thenReturn(SphereJsonUtils.readObjectFromResource(MOCKS + "messagesResult_empty.json",
                PagedQueryWithMessages));

        assertThat(messageReader.read()).isNull();
    }

    @Test
    public void read_whenOnePage_returnsResultFromThePage() {
        when(client.executeBlocking(any())).thenReturn(SphereJsonUtils.readObjectFromResource(MOCKS + "messagesResult_1.json",
                PagedQueryWithMessages));
        PaymentTransactionStateChangedMessage firstUnprocessedMessage = messageReader.read();
        assertThat(firstUnprocessedMessage).isNotNull();
        assertThat(firstUnprocessedMessage.getId()).isEqualTo("11111111-1111-1111-1111-111111111111");

        PaymentTransactionStateChangedMessage secondUnprocessedMessage = messageReader.read();
        assertThat(secondUnprocessedMessage).isNotNull();
        assertThat(secondUnprocessedMessage.getId()).isEqualTo("44444444-4444-4444-4444-444444444444");

        // sphere client should be called only once even if we call messageReader.read twice,
        // because both results are on the same page
        verify(client, times(1)).executeBlocking(any());

        // no messages processed - last timestamp is not updated
        timeStampManager.persistLastProcessedMessageTimeStamp();
        assertThat(timeStampManager.getLastProcessedMessageTimeStamp()).isNull();
    }

    @Test
    public void read_whenFirstPageIsEmpty_returnsResultFromSecondPage_AndPersistsTimestamp() {
        PagedQueryResult<PaymentTransactionStateChangedMessage> pagedFirstResult = SphereJsonUtils.readObjectFromResource(MOCKS + "messagesResult_1.json",
                PagedQueryWithMessages);
        PagedQueryResult<PaymentTransactionStateChangedMessage> pagedSecondResult = SphereJsonUtils.readObjectFromResource(MOCKS + "messagesResult_2.json",
                PagedQueryWithMessages);
        List<PaymentTransactionStateChangedMessage> firstResults = pagedFirstResult.getResults();
        firstResults.forEach(messageProcessedManager::setMessageIsProcessed);

        when(client.executeBlocking(any(MessageQuery.class))).thenAnswer(a -> {
            MessageQuery query = a.getArgumentAt(0, MessageQuery.class);
            if (query.offset() == 0) {
                return pagedFirstResult;
            } else {
                return pagedSecondResult;
            }
        });

        PaymentTransactionStateChangedMessage firstMessage = messageReader.read();
        assertThat(firstMessage).isNotNull();
        assertThat(firstMessage.getId()).isEqualTo("111");

        PaymentTransactionStateChangedMessage secondMessage = messageReader.read();
        assertThat(secondMessage).isNotNull();
        assertThat(secondMessage.getId()).isEqualTo("444");

        // sphere client called twice to fetch 2 pages
        verify(client, times(2)).executeBlocking(any());

        // verify last timestamp is equal to last processed message
        PaymentTransactionStateChangedMessage lastResultOnFirstPage = firstResults.get(firstResults.size() - 1);
        timeStampManager.persistLastProcessedMessageTimeStamp();
        assertThat(timeStampManager.getLastProcessedMessageTimeStamp()).isEqualTo(lastResultOnFirstPage.getLastModifiedAt());
    }
}