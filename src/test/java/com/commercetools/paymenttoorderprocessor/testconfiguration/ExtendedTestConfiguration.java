package com.commercetools.paymenttoorderprocessor.testconfiguration;

import com.commercetools.paymenttoorderprocessor.jobs.actions.MessageFilter;
import com.commercetools.paymenttoorderprocessor.jobs.actions.MessageReader;
import com.commercetools.paymenttoorderprocessor.jobs.actions.OrderCreator;
import com.commercetools.paymenttoorderprocessor.paymentcreationconfigurationmanager.PaymentCreationConfigurationManager;
import com.commercetools.paymenttoorderprocessor.paymentcreationconfigurationmanager.PaymentCreationConfigurationManagerImpl;
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.time.ZonedDateTime;

@Configuration
public class ExtendedTestConfiguration {

    @Bean
    public TimeStampManager timeStampManager() {
        return new TimeStampManager() {

            private ZonedDateTime actualProcessedMessageTimeStamp = null;

            //by default just get Messages from last 2 minutes
            private ZonedDateTime lastProcessedMessageTimeStamp = ZonedDateTime.now().minusMinutes(2L);

            private boolean processingMessageFailed = false;

            @Override
            public void setActualProcessedMessageTimeStamp(ZonedDateTime timeStamp) {
                if (!processingMessageFailed) {
                    actualProcessedMessageTimeStamp = timeStamp;
                }
            }

            @Override
            public void persistLastProcessedMessageTimeStamp() {
                lastProcessedMessageTimeStamp = actualProcessedMessageTimeStamp;
            }

            @Override
            public ZonedDateTime getLastProcessedMessageTimeStamp() {
                return lastProcessedMessageTimeStamp;
            }

            @Override
            public void processingMessageFailed() {
                processingMessageFailed = true;
            }
        };
    }

    @Bean
    PaymentCreationConfigurationManager paymentCreationConfigurationManager() {
        return new PaymentCreationConfigurationManagerImpl();
    }

    @Bean
    MessageFilter messageProcessor() {
        return new MessageFilter();
    }

    /**
     * For each test we need own instance of messageReader because its not stateless.
     */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public MessageReader messageReader() {
        return new MessageReader();
    }

    @Bean
    public OrderCreator orderCreator() {
        return new OrderCreator();
    }
}
