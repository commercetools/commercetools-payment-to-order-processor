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

        when(client.executeBlocking(any())).thenReturn(emptyMessagesResult);

        assertThat(messageReader.read()).isNull();
    }

    @Test
    public void read_whenOnePage_returnsResultFromThePage() {
        when(client.executeBlocking(any())).thenAnswer(a -> {
            MessageQuery query = a.getArgumentAt(0, MessageQuery.class);
            if (query.offset() == 0) {
                return firstMessagesResult;
            } else {
                return emptyMessagesResult;
            }
        });
        PaymentTransactionCreatedOrUpdatedMessage firstUnprocessedMessage = messageReader.read();
        assertThat(firstUnprocessedMessage).isNotNull();
        assertThat(firstUnprocessedMessage.getId()).isEqualTo("11111111-1111-1111-1111-111111111111");

        PaymentTransactionCreatedOrUpdatedMessage secondUnprocessedMessage = messageReader.read();
        assertThat(secondUnprocessedMessage).isNotNull();
        assertThat(secondUnprocessedMessage.getId()).isEqualTo("44444444-4444-4444-4444-444444444444");

        assertThat(messageReader.read()).isNull(); // empty queue after first page

        // sphere client should be called only twice (first and empty result page)
        // even if we call messageReader.read 3 times, because both results are on the same page first page
        verify(client, times(2)).executeBlocking(any());

        // no messages processed - last timestamp is not updated
        timeStampManager.persistLastProcessedMessageTimeStamp();
        assertThat(timeStampManager.getLastProcessedMessageTimeStamp()).isNull();
    }

    @Test
    public void read_whenFirstPageIsEmpty_returnsResultFromSecondPage_AndPersistsTimestamp() {
        // mark results from the first page as processed
        List<PaymentTransactionCreatedOrUpdatedMessage> firstResults = firstMessagesResult.getResults();

        mock2PagesResult(client, messageReader);

        PaymentTransactionCreatedOrUpdatedMessage firstMessage = messageReader.read();
        assertThat(firstMessage).isNotNull();
        assertThat(firstMessage.getId()).isEqualTo("111");

        PaymentTransactionCreatedOrUpdatedMessage secondMessage = messageReader.read();
        assertThat(secondMessage).isNotNull();
        assertThat(secondMessage.getId()).isEqualTo("444");

        assertThat(messageReader.read()).isNull(); // empty queue after second page

        // sphere client called 3 to fetch 2 filled and 1 empty pages
        verify(client, times(3)).executeBlocking(any());

        // verify last timestamp is equal to last processed message
        PaymentTransactionCreatedOrUpdatedMessage lastResultOnFirstPage = firstResults.get(firstResults.size() - 1);
        timeStampManager.persistLastProcessedMessageTimeStamp();
        assertThat(timeStampManager.getLastProcessedMessageTimeStamp()).isEqualTo(lastResultOnFirstPage.getLastModifiedAt());
    }

    @Test
    public void read_someResultsAreProcessedOnFirstAndSecondPage_returnsResultFromFirstAndSecondPage_AndPersistsTimestamp() {
        // mark one result from first and one from second page as processed
        List<PaymentTransactionCreatedOrUpdatedMessage> firstResults = firstMessagesResult.getResults();
        List<PaymentTransactionCreatedOrUpdatedMessage> secondResults = secondMessagesResult.getResults();
        PaymentTransactionCreatedOrUpdatedMessage lastMessage = secondResults.get(1);

        mock2PagesResult(client, messageReader);

        PaymentTransactionCreatedOrUpdatedMessage firstMessage = messageReader.read();
        assertThat(firstMessage).isNotNull();
        assertThat(firstMessage.getId()).isEqualTo("44444444-4444-4444-4444-444444444444");

        PaymentTransactionCreatedOrUpdatedMessage secondMessage = messageReader.read();
        assertThat(secondMessage).isNotNull();
        assertThat(secondMessage.getId()).isEqualTo("111");

        assertThat(messageReader.read()).isNull(); // empty queue after second page

        // sphere client called 3 to fetch 2 filled and 1 empty pages
        verify(client, times(3)).executeBlocking(any());

        // verify last timestamp is equal to last processed message
        timeStampManager.persistLastProcessedMessageTimeStamp();
        assertThat(timeStampManager.getLastProcessedMessageTimeStamp()).isEqualTo(lastMessage.getLastModifiedAt());
    }

    private void mock2PagesResult(final BlockingSphereClient client, final MessageReader messageReader) {
        when(client.executeBlocking(any(MessageQuery.class))).thenAnswer(a -> {
            MessageQuery query = a.getArgumentAt(0, MessageQuery.class);
            if (query.offset() == 0) {
                return firstMessagesResult;
            } else if (query.offset() <= (messageReader.RESULTS_PER_PAGE - messageReader.PAGE_OVERLAP)) {
                return secondMessagesResult;
            } else {
                return emptyMessagesResult;
            }
        });
    }
}
