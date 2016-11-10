package com.commercetools.paymenttoorderprocessor.testconfiguration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManagerImpl;

@Configuration
public class TimeStampTestConfiguration {
    @Bean
    public TimeStampManager timeStampManager() {
        return new TimeStampManagerImpl();
    }
}