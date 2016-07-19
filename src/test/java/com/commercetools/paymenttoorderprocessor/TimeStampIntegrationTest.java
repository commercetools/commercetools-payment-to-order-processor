package com.commercetools.paymenttoorderprocessor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import com.commercetools.paymenttoorderprocessor.fixtures.TimeStampFixtures;
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManagerImpl;

import io.sphere.sdk.client.BlockingSphereClient;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader=AnnotationConfigContextLoader.class)
public class TimeStampIntegrationTest extends IntegrationTest {

    public static final Logger LOG = LoggerFactory.getLogger(TimeStampIntegrationTest.class);
    
    @Configuration
    public static class ContextConfiguration {
        @Bean 
        public BlockingSphereClient client() {
            return testClient();
        }
        @Bean
        public TimeStampManager timeStampManager() {
            return new TimeStampManagerImpl();
        }
        @Bean
        public static PropertySourcesPlaceholderConfigurer properties() throws Exception {
            final PropertySourcesPlaceholderConfigurer pspc = new PropertySourcesPlaceholderConfigurer();
            Properties properties = new Properties();
            properties.setProperty("ctp.this.servicename", "commercetools-payment-to-order-processor");

            pspc.setProperties(properties);
            return pspc;
        }
    }
    @Value("${ctp.this.servicename}")
    private String container;

    @Autowired
    private TimeStampManager timeStampManager;
    
    @Test
    public void noTimeStamp() throws Exception {
        TimeStampFixtures.removeTimeStamps(testClient(),container);
        assertThat(timeStampManager.getLastProcessedMessageTimeStamp()).isNotPresent();
    }
    
    @Test
    public void withTimeStamp() throws Exception {
        TimeStampFixtures.withTimeStamp(testClient(), container, timeStamp -> {
            assertThat(timeStampManager.getLastProcessedMessageTimeStamp()).isPresent();
            assertThat(timeStampManager.getLastProcessedMessageTimeStamp().get()).isEqualTo(timeStamp.getLastTimeStamp());
            return timeStamp;
        });
    }
}