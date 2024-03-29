package com.commercetools.paymenttoorderprocessor.jobs.actions;

import com.commercetools.paymenttoorderprocessor.dto.PaymentTransactionCreatedOrUpdatedMessage;
import com.commercetools.paymenttoorderprocessor.testconfiguration.ExtendedTestConfiguration;
import com.commercetools.paymenttoorderprocessor.testconfiguration.HttpClientMockConfiguration;
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;
import com.fasterxml.jackson.core.type.TypeReference;
import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.json.SphereJsonUtils;
import io.sphere.sdk.messages.queries.MessageQuery;
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

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(
        classes = {
                MessageReaderTest.TestConfig.class, HttpClientMockConfiguration.class, ExtendedTestConfiguration.class
        },
        initializers = ConfigFileApplicationContextInitializer.class)
// clean MessageReader on every test
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

    private static final TypeReference<PagedQueryResult<PaymentTransactionCreatedOrUpdatedMessage>> PagedQueryWithMessages =
            new TypeReference<PagedQueryResult<PaymentTransactionCreatedOrUpdatedMessage>>() {
            };

    private static final PagedQueryResult<PaymentTransactionCreatedOrUpdatedMessage> emptyMessagesResult =
            SphereJsonUtils.readObjectFromResource(MOCKS + "messagesResult_empty.json", PagedQueryWithMessages);

    private static final PagedQueryResult<PaymentTransactionCreatedOrUpdatedMessage> firstMessagesResult =
            SphereJsonUtils.readObjectFromResource(MOCKS + "messagesResult_1.json", PagedQueryWithMessages);

    private static final PagedQueryResult<PaymentTransactionCreatedOrUpdatedMessage> secondMessagesResult =
            SphereJsonUtils.readObjectFromResource(MOCKS + "messagesResult_2.json", PagedQueryWithMessages);

    @Autowired
    private MessageReader messageReader;

    @Autowired
    private TimeStampManager timeStampManager;

    @Autowired
    private BlockingSphereClient client;

    @Test
    public void read_whenEmpty_returnsNull() {
        when(client.execute(any())).thenReturn(completedFuture(emptyMessagesResult));
        assertThat(messageReader.read()).isNull();
    }

    @Test
    public void read_whenOnePage_returnsResultFromThePage() {
        when(client.execute(any())).thenAnswer(a -> {
            return completedFuture(firstMessagesResult);
        });

    PaymentTransactionCreatedOrUpdatedMessage firstUnprocessedMessage = messageReader.read();
        assertThat(firstUnprocessedMessage).isNotNull();
        assertThat(firstUnprocessedMessage.getId()).isEqualTo("11111111-1111-1111-1111-111111111111");

        PaymentTransactionCreatedOrUpdatedMessage secondUnprocessedMessage = messageReader.read();
        assertThat(secondUnprocessedMessage).isNotNull();
        assertThat(secondUnprocessedMessage.getId()).isEqualTo("44444444-4444-4444-4444-444444444444");

        assertThat(messageReader.read()).isNull(); // empty queue after first page


        verify(client, times(1)).execute(any());
    }

    @Test
    public void read_whenTwoPages_returnsFourMessagesFromBothPages() {
       when(client.execute(any())).thenAnswer(a -> {
            return completedFuture(secondMessagesResult);
        });


        PaymentTransactionCreatedOrUpdatedMessage firstMessage = messageReader.read();
        assertThat(firstMessage).isNotNull();
        assertThat(firstMessage.getId()).isEqualTo("11111111-1111-1111-1111-111111111111");

        PaymentTransactionCreatedOrUpdatedMessage secondMessage = messageReader.read();
        assertThat(secondMessage).isNotNull();
        assertThat(secondMessage.getId()).isEqualTo("44444444-4444-4444-4444-444444444444");

        PaymentTransactionCreatedOrUpdatedMessage thirdMessage = messageReader.read();
        assertThat(thirdMessage).isNotNull();
        assertThat(thirdMessage.getId()).isEqualTo("111");

        PaymentTransactionCreatedOrUpdatedMessage fourthMessage = messageReader.read();
        assertThat(fourthMessage).isNotNull();
        assertThat(fourthMessage.getId()).isEqualTo("444");

        assertThat(messageReader.read()).isNull(); // empty queue after second page

        // sphere client called 3 to fetch 2 filled and 1 empty pages
        verify(client, times(1)).execute(any());
    }
}
