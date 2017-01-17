package com.commercetools.paymenttoorderprocessor.testconfiguration;

import com.commercetools.paymenttoorderprocessor.customobjects.MessageProcessedManager;
import com.commercetools.paymenttoorderprocessor.jobs.actions.MessageFilter;
import com.commercetools.paymenttoorderprocessor.jobs.actions.MessageReader;
import com.commercetools.paymenttoorderprocessor.jobs.actions.OrderCreator;
import com.commercetools.paymenttoorderprocessor.paymentcreationconfigurationmanager.PaymentCreationConfigurationManager;
import com.commercetools.paymenttoorderprocessor.paymentcreationconfigurationmanager.PaymentCreationConfigurationManagerImpl;
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;
import io.sphere.sdk.messages.Message;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Optional;

@Configuration
public class ExtendedTestConfiguration {
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
                //just get Messages from last 2 minutes
                return Optional.of(ZonedDateTime.now().minusMinutes(2L));
            }

            @Override
            public void processingMessageFailed() {
                //not needed in test
            }
        };
    }

    @Bean
    public MessageProcessedManager messageProcessedManager() {
        return new MessageProcessedManager() {

            private HashSet<Message> processedMessages = new HashSet<>();

            @Override
            public void setMessageIsProcessed(Message message) {
                processedMessages.add(message);
            }

            @Override
            public boolean isMessageUnprocessed(Message message) {
                return !processedMessages.contains(message);
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
