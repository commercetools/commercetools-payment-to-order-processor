package com.commercetools.paymenttoorderprocessor.testconfiguration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.commercetools.paymenttoorderprocessor.jobs.actions.MessageReader;

@Configuration
public class ReaderTestConfiguration2 {
    @Bean MessageReader messageReader() {
        return new MessageReader();
    }
}