package com.commercetools.paymenttoorderprocessor.jobs.actions;

import com.commercetools.paymenttoorderprocessor.ShereClientConfiguration;
import com.commercetools.paymenttoorderprocessor.customobjects.MessageProcessedManager;
import com.commercetools.paymenttoorderprocessor.fixtures.PaymentFixtures;
import com.commercetools.paymenttoorderprocessor.helper.CartAndMessageCreateHelper;
import com.commercetools.paymenttoorderprocessor.testconfiguration.BasicTestConfiguration;
import com.commercetools.paymenttoorderprocessor.testconfiguration.ExtendedTestConfiguration;
import com.commercetools.paymenttoorderprocessor.testconfiguration.HttpClientMock;
import com.commercetools.paymenttoorderprocessor.testconfiguration.HttpClientMockConfiguration;
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;
import com.commercetools.paymenttoorderprocessor.wrapper.CartAndMessage;
import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.http.HttpStatusCode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.ZonedDateTime;
import java.util.Collections;

import static com.commercetools.paymenttoorderprocessor.fixtures.PaymentFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {BasicTestConfiguration.class, ExtendedTestConfiguration.class,
        ShereClientConfiguration.class, HttpClientMockConfiguration.class},
        initializers = ConfigFileApplicationContextInitializer.class,
        loader = SpringBootContextLoader.class)
// clean context to create new singletons after every test, because they are stateful!!!
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class OrderCreatorIntegrationTest {

    @Autowired
    private MessageProcessedManager messageProcessedManager;

    @Autowired
    private TimeStampManager timeStampManager;

    @Autowired
    private CartAndMessageCreateHelper cartAndMessageCreateHelper;

    @Autowired
    private OrderCreator orderCreator;

    @Autowired
    private HttpClientMock httpClientMock;

    @Autowired
    private BlockingSphereClient testClient;

    @Test
    public void writeWith201CreatedResult() throws Exception {
        CartAndMessage cartAndMessage1 = cartAndMessageCreateHelper.createCartAndMessage(
                PaymentFixtures.withPayment(testClient, builder -> builder.amountPlanned(EUR_20), a -> a));

        httpClientMock.spyStatusCode(HttpStatusCode.CREATED_201);
        assertThat(messageProcessedManager.isMessageUnprocessed(cartAndMessage1.getMessage())).isTrue();
        orderCreator.write(Collections.singletonList(cartAndMessage1));
        assertThat(messageProcessedManager.isMessageUnprocessed(cartAndMessage1.getMessage())).isFalse();

        // timestamp should be updated, because all operations were successful
        timeStampManager.setActualProcessedMessageTimeStamp(cartAndMessage1.getMessage().getLastModifiedAt());
        persistAndAssertTimestampEquals(cartAndMessage1.getMessage().getLastModifiedAt());

        ZonedDateTime now = ZonedDateTime.now();
        timeStampManager.setActualProcessedMessageTimeStamp(now);
        persistAndAssertTimestampEquals(now);
    }

    @Test
    public void writeWith200OkResult() throws Exception {
        CartAndMessage cartAndMessage2 = cartAndMessageCreateHelper.createCartAndMessage(
                PaymentFixtures.withPayment(testClient, builder -> builder.amountPlanned(USD_30), a -> a));

        httpClientMock.spyStatusCode(HttpStatusCode.OK_200);
        assertThat(messageProcessedManager.isMessageUnprocessed(cartAndMessage2.getMessage())).isTrue();
        orderCreator.write(Collections.singletonList(cartAndMessage2));
        assertThat(messageProcessedManager.isMessageUnprocessed(cartAndMessage2.getMessage())).isFalse();

        // timestamp should be updated, because result is 200
        timeStampManager.setActualProcessedMessageTimeStamp(cartAndMessage2.getMessage().getLastModifiedAt());
        persistAndAssertTimestampEquals(cartAndMessage2.getMessage().getLastModifiedAt());

        ZonedDateTime now = ZonedDateTime.now();
        timeStampManager.setActualProcessedMessageTimeStamp(now);
        persistAndAssertTimestampEquals(now);
    }

    @Test
    public void writeWith400BadResult() throws Exception {
        CartAndMessage cartAndMessage3 = cartAndMessageCreateHelper.createCartAndMessage(
                PaymentFixtures.withPayment(testClient, builder -> builder.amountPlanned(UAH_42), a -> a));

        timeStampManager.setActualProcessedMessageTimeStamp(cartAndMessage3.getMessage().getLastModifiedAt());

        httpClientMock.spyResponse(HttpStatusCode.BAD_REQUEST_400, "Test 400 bad response");
        assertThat(messageProcessedManager.isMessageUnprocessed(cartAndMessage3.getMessage())).isTrue();
        orderCreator.write(Collections.singletonList(cartAndMessage3));
        assertThat(messageProcessedManager.isMessageUnprocessed(cartAndMessage3.getMessage()))
                .withFailMessage("Message should not be marked as processed")
                .isTrue();

        timeStampManager.setActualProcessedMessageTimeStamp(ZonedDateTime.now());
        persistAndAssertTimestampEquals(cartAndMessage3.getMessage().getLastModifiedAt());

        httpClientMock.spyResponse(HttpStatusCode.FORBIDDEN_403, "Test 403 bad response");
        assertThat(messageProcessedManager.isMessageUnprocessed(cartAndMessage3.getMessage())).isTrue();
        orderCreator.write(Collections.singletonList(cartAndMessage3));
        assertThat(messageProcessedManager.isMessageUnprocessed(cartAndMessage3.getMessage()))
                .withFailMessage("Message should not be marked as processed")
                .isTrue();

        // timestamp is not changeable any more, since above we had an error
        timeStampManager.setActualProcessedMessageTimeStamp(ZonedDateTime.now());
        persistAndAssertTimestampEquals(cartAndMessage3.getMessage().getLastModifiedAt());

        httpClientMock.spyResponse(HttpStatusCode.BAD_GATEWAY_502, "Test 502 response");
        assertThat(messageProcessedManager.isMessageUnprocessed(cartAndMessage3.getMessage())).isTrue();
        orderCreator.write(Collections.singletonList(cartAndMessage3));
        assertThat(messageProcessedManager.isMessageUnprocessed(cartAndMessage3.getMessage()))
                .withFailMessage("Message should not be marked as processed")
                .isTrue();

        // timestamp is not changeable any more, since above we had an error
        timeStampManager.setActualProcessedMessageTimeStamp(ZonedDateTime.now());
        persistAndAssertTimestampEquals(cartAndMessage3.getMessage().getLastModifiedAt());

        httpClientMock.spyResponse(HttpStatusCode.CREATED_201);
        assertThat(messageProcessedManager.isMessageUnprocessed(cartAndMessage3.getMessage())).isTrue();
        orderCreator.write(Collections.singletonList(cartAndMessage3));
        assertThat(messageProcessedManager.isMessageUnprocessed(cartAndMessage3.getMessage()))
                .withFailMessage("Message should be marked as processed")
                .isFalse();

        // even if the last operation is success - timestamp should not be untouched,
        // because previous operations failed
        timeStampManager.setActualProcessedMessageTimeStamp(ZonedDateTime.now());
        persistAndAssertTimestampEquals(cartAndMessage3.getMessage().getLastModifiedAt());
    }

    private void persistAndAssertTimestampEquals(ZonedDateTime actualProcessedMessageTimeStamp) {
        timeStampManager.persistLastProcessedMessageTimeStamp();

        assertThat(timeStampManager.getLastProcessedMessageTimeStamp())
                .isEqualTo(actualProcessedMessageTimeStamp);

    }

}

