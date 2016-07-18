package com.commercetools.paymenttoorderprocessor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.client.ErrorResponseException;
import io.sphere.sdk.client.SphereClient;
import io.sphere.sdk.client.SphereClientConfig;
import io.sphere.sdk.client.SphereClientFactory;

//SetUp for Test
public class IntegrationTest {
    
    private static BlockingSphereClient client;
    
    protected synchronized static BlockingSphereClient testClient() {
        setupClient();
        return client;
    }
    
    private static void setupClient() {
        if (client == null) {
            SphereClientConfig config = getSphereClientConfig();
            final SphereClient sphereClient  = SphereClientFactory.of().createClient(config.getProjectKey(), config.getClientId(), config.getClientSecret());
            client = BlockingSphereClient.of(sphereClient, 50000, TimeUnit.MILLISECONDS);
        }
    }
    
    private static SphereClientConfig getSphereClientConfig() {
        File file = new File("integrationtest.properties");
        return loadViaProperties(file);
    }
    
    private static SphereClientConfig loadViaProperties(final File file) {
        try (final FileInputStream fileInputStream = new FileInputStream(file)) {
            final Properties properties = new Properties();
            properties.load(fileInputStream);
            return SphereClientConfig.ofProperties(properties, "");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
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
        assertEventually(Duration.ofMinutes(3L), Duration.ofMillis(1000), block);
    }
}
