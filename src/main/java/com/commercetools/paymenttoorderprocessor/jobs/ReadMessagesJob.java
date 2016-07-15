package com.commercetools.paymenttoorderprocessor.jobs;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import com.commercetools.paymenttoorderprocessor.jobs.actions.MessageProcessor;
import com.commercetools.paymenttoorderprocessor.jobs.actions.MessageReader;
import com.commercetools.paymenttoorderprocessor.jobs.actions.MessageWriter;

import io.sphere.sdk.messages.Message;

@Configuration
@EnableBatchProcessing
public class ReadMessagesJob {
    private static final String STEP_LOAD_MESSAGES = "loadMessages";
    
    @Autowired
    private JobBuilderFactory jobs;

    @Autowired
    private StepBuilderFactory steps;

    @Bean
    @DependsOn("blockingSphereClient")
    public ItemReader<Message> reader() {
        return new MessageReader();
    }

    @Bean
    public ItemProcessor<Message, Message> processor() {
        return new MessageProcessor();
    }

    @Bean
    public ItemWriter<Message> writer() {
        return new MessageWriter();
    }

    @Bean
    public Job createJob(@Qualifier(STEP_LOAD_MESSAGES) final Step loadMessages) {
        return jobs.get("readMessagesJob").start(loadMessages).build();
    }

    @Bean
    public Step loadMessages(ItemReader<Message> reader, 
            ItemProcessor<Message, Message> processor,
            ItemWriter<Message> writer) {
        return steps.get(STEP_LOAD_MESSAGES)
                .<Message, Message> chunk(1)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
