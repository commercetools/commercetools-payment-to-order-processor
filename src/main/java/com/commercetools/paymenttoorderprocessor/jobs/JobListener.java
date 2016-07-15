package com.commercetools.paymenttoorderprocessor.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.beans.factory.annotation.Autowired;

import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;

public class JobListener implements JobExecutionListener {

    public static final Logger LOG = LoggerFactory.getLogger(JobListener.class);

    @Autowired
    private TimeStampManager timeStampManager;

    @Override
    public void afterJob(JobExecution jobExecution) {
        if(jobExecution.getStatus() == BatchStatus.COMPLETED) {
            LOG.info("Job finished. Saving new LastProcessedMessageTimeStamp to CustomObject");
            timeStampManager.persistLastProcessedMessageTimeStamp();
        }
        else {
            LOG.error("Job did not complete. BatchStatus is {}", jobExecution.getStatus());
        }
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        // TODO Auto-generated method stub
    }
}

