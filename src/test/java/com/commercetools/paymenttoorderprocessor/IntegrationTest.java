package com.commercetools.paymenttoorderprocessor;

import java.time.Duration;

import io.sphere.sdk.client.ErrorResponseException;

public class IntegrationTest {

    public static void assertEventually(final Duration maxWaitTime, final Duration waitBeforeRetry, final Runnable block) {
        final long timeOutAt = System.currentTimeMillis() + maxWaitTime.toMillis();
        while (true) {
            try {
                block.run();

                // the block executed without throwing an exception, return
                return;
            } catch (AssertionError | ErrorResponseException e) {
                if (e instanceof ErrorResponseException && !((ErrorResponseException) e).hasErrorCode("SearchFacetPathNotFound")) {
                    throw e;
                }
                if (System.currentTimeMillis() > timeOutAt) {
                    throw e;
                }
            }

            try {
                Thread.sleep(waitBeforeRetry.toMillis());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    public static void assertEventually(final Runnable block) {
        assertEventually(Duration.ofMinutes(4L), Duration.ofMillis(1000), block);
    }
}
