package com.commercetools.paymenttoorderprocessor.jobs.actions;

import com.commercetools.paymenttoorderprocessor.ShereClientConfiguration;
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
        PaymentFixtures.withPayment(testClient, builder -> builder.amountPlanned(EUR_20), payment -> {
            CartAndMessage cartAndMessage1 = cartAndMessageCreateHelper.createCartAndMessage(payment);

            httpClientMock.spyStatusCode(HttpStatusCode.CREATED_201);
            orderCreator.write(Collections.singletonList(cartAndMessage1));

            // timestamp should be updated, because all operations were successful
            timeStampManager.setActualProcessedMessageTimeStamp(cartAndMessage1.getMessage().getLastModifiedAt());
            persistAndAssertTimestampEquals(cartAndMessage1.getMessage().getLastModifiedAt());

            ZonedDateTime now = ZonedDateTime.now();
            timeStampManager.setActualProcessedMessageTimeStamp(now);
            persistAndAssertTimestampEquals(now);

            return payment;
        });
    }

    @Test
    public void writeWith200OkResult() throws Exception {
        PaymentFixtures.withPayment(testClient, builder -> builder.amountPlanned(USD_30), payment -> {
            CartAndMessage cartAndMessage2 = cartAndMessageCreateHelper.createCartAndMessage(payment);

            httpClientMock.spyStatusCode(HttpStatusCode.OK_200);
            orderCreator.write(Collections.singletonList(cartAndMessage2));

            // timestamp should be updated, because result is 200
            timeStampManager.setActualProcessedMessageTimeStamp(cartAndMessage2.getMessage().getLastModifiedAt());
            persistAndAssertTimestampEquals(cartAndMessage2.getMessage().getLastModifiedAt());

            ZonedDateTime now = ZonedDateTime.now();
            timeStampManager.setActualProcessedMessageTimeStamp(now);
            persistAndAssertTimestampEquals(now);

            return payment;
        });
    }

    @Test
    public void writeWith400BadResult() throws Exception {
        PaymentFixtures.withPayment(testClient, builder -> builder.amountPlanned(UAH_42), payment -> {
            CartAndMessage cartAndMessage3 = cartAndMessageCreateHelper.createCartAndMessage(payment);

            timeStampManager.setActualProcessedMessageTimeStamp(cartAndMessage3.getMessage().getLastModifiedAt());

            httpClientMock.spyResponse(HttpStatusCode.BAD_REQUEST_400, "Test 400 bad response");
            orderCreator.write(Collections.singletonList(cartAndMessage3));

            timeStampManager.setActualProcessedMessageTimeStamp(ZonedDateTime.now());
            persistAndAssertTimestampEquals(cartAndMessage3.getMessage().getLastModifiedAt());

            httpClientMock.spyResponse(HttpStatusCode.FORBIDDEN_403, "Test 403 bad response");
            orderCreator.write(Collections.singletonList(cartAndMessage3));

            // timestamp is not changeable any more, since above we had an error
            timeStampManager.setActualProcessedMessageTimeStamp(ZonedDateTime.now());
            persistAndAssertTimestampEquals(cartAndMessage3.getMessage().getLastModifiedAt());

            httpClientMock.spyResponse(HttpStatusCode.BAD_GATEWAY_502, "Test 502 response");
            orderCreator.write(Collections.singletonList(cartAndMessage3));

            // timestamp is not changeable any more, since above we had an error
            timeStampManager.setActualProcessedMessageTimeStamp(ZonedDateTime.now());
            persistAndAssertTimestampEquals(cartAndMessage3.getMessage().getLastModifiedAt());

            httpClientMock.spyResponse(HttpStatusCode.CREATED_201);
            orderCreator.write(Collections.singletonList(cartAndMessage3));

            // even if the last operation is success - timestamp should not be untouched,
            // because previous operations failed
            timeStampManager.setActualProcessedMessageTimeStamp(ZonedDateTime.now());
            persistAndAssertTimestampEquals(cartAndMessage3.getMessage().getLastModifiedAt());

            return payment;
        });
    }

    private void persistAndAssertTimestampEquals(ZonedDateTime actualProcessedMessageTimeStamp) {
        timeStampManager.persistLastProcessedMessageTimeStamp();

        assertThat(timeStampManager.getLastProcessedMessageTimeStamp())
                .isEqualTo(actualProcessedMessageTimeStamp);

    }

}

