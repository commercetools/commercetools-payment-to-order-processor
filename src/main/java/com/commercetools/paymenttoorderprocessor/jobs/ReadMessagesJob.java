package com.commercetools.paymenttoorderprocessor.jobs;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
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
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManagerImpl;

import io.sphere.sdk.carts.Cart;
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
    @DependsOn({"blockingSphereClient", "timeStampManager"})
    public ItemReader<Message> reader() {
        return new MessageReader();
    }

    @Bean
    @DependsOn("blockingSphereClient")
    public ItemProcessor<Message, Cart> processor() {
        return new MessageProcessor();
    }

    @Bean
    public ItemWriter<Cart> writer() {
        return new MessageWriter();
    }

    @Bean
    @DependsOn("timeStampManager")
    public JobExecutionListener listener() {
        return new JobListener();
    }

    @Bean
    @DependsOn("blockingSphereClient")
    public TimeStampManager timeStampManager() {
        return new TimeStampManagerImpl();
    }

    @Bean
    public Job createJob(@Qualifier(STEP_LOAD_MESSAGES) final Step loadMessages, final JobExecutionListener listener) {
        return jobs.get("readMessagesJob").listener(listener).start(loadMessages).build();
    }

    @Bean
    public Step loadMessages(ItemReader<Message> reader, 
            ItemProcessor<Message, Cart> processor,
            ItemWriter<Cart> writer) {
        return steps.get(STEP_LOAD_MESSAGES)
                .<Message, Cart> chunk(1)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
