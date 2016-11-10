package com.commercetools.paymenttoorderprocessor.testconfiguration;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.commercetools.paymenttoorderprocessor.customobjects.MessageProcessedManager;
import com.commercetools.paymenttoorderprocessor.jobs.actions.MessageFilter;
import com.commercetools.paymenttoorderprocessor.jobs.actions.MessageReader;
import com.commercetools.paymenttoorderprocessor.paymentcreationconfigurationmanager.PaymentCreationConfigurationManager;
import com.commercetools.paymenttoorderprocessor.paymentcreationconfigurationmanager.PaymentCreationConfigurationManagerImpl;
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;

import io.sphere.sdk.messages.Message;

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
            
            @Override
            public void setMessageIsProcessed(Message message) {
                //not needed in test
            }
            
            @Override
            public boolean isMessageUnprocessed(Message message) {
                //get all messages
                return true;
            }
        };
    }
    
    @Bean PaymentCreationConfigurationManager paymentCreationConfigurationManager() {
        return new PaymentCreationConfigurationManagerImpl();
    }
    
    @Bean MessageFilter messageProcessor() {
        return new MessageFilter();
    }
}
