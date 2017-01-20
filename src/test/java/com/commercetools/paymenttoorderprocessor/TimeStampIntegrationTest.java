package com.commercetools.paymenttoorderprocessor;

import com.commercetools.paymenttoorderprocessor.fixtures.TimeStampFixtures;
import com.commercetools.paymenttoorderprocessor.testconfiguration.BasicTestConfiguration;
import com.commercetools.paymenttoorderprocessor.testconfiguration.TimeStampTestConfiguration;
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManagerImpl;
import io.sphere.sdk.client.BlockingSphereClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {BasicTestConfiguration.class, TimeStampTestConfiguration.class, ShereClientConfiguration.class},
        initializers = ConfigFileApplicationContextInitializer.class,
        loader = SpringBootContextLoader.class)
public class TimeStampIntegrationTest extends IntegrationTest {

    @Configuration
    public static class ContextConfiguration {
        @Bean
        public TimeStampManager timeStampManager() {
            return new TimeStampManagerImpl();
        }
    }
    @Value("${ctp.custom.object.containername}")
    private String container;

    private TimeStampManager timeStampManager;

    @Autowired
    private BlockingSphereClient testClient;

    @Before
    public void init() {
        timeStampManager = new TimeStampManagerImpl(container, testClient);
    }

    @Test
    public void noTimeStamp() throws Exception {
        TimeStampFixtures.removeTimeStamps(testClient ,container);
        assertThat(timeStampManager.getLastProcessedMessageTimeStamp()).isNull();
    }

    @Test
    public void withTimeStamp() throws Exception {
        TimeStampFixtures.withTimeStamp(testClient, container, timeStamp -> {
            assertThat(timeStampManager.getLastProcessedMessageTimeStamp()).isNotNull();
            assertThat(timeStampManager.getLastProcessedMessageTimeStamp()).isEqualTo(timeStamp.getLastTimeStamp());
            return timeStamp;
        });
    }

}