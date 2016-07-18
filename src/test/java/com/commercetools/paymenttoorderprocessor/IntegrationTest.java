package com.commercetools.paymenttoorderprocessor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.client.SphereClient;
import io.sphere.sdk.client.SphereClientConfig;
import io.sphere.sdk.client.SphereClientFactory;

//SetUp for Test
public class IntegrationTest {
    
    private static BlockingSphereClient client;
    
    protected synchronized static BlockingSphereClient client() {
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
}
