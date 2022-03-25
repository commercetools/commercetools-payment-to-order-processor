package com.commercetools.paymenttoorderprocessor.jobs;

import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Locale;

public class JobListener implements JobExecutionListener {

    public static final Logger LOG = LoggerFactory.getLogger(JobListener.class);
    public static final int INCONSISTENCY_MINUTES = 3;

    @Autowired
    private TimeStampManager timeStampManager;

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            timeStampManager.persistLastProcessedMessageTimeStamp();
        } else {
            LOG.error("Job did not complete. BatchStatus is {}", jobExecution.getStatus());
        }
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        timeStampManager.setActualProcessedMessageTimeStamp(ZonedDateTime.now().minusMinutes(INCONSISTENCY_MINUTES));
    }
}

