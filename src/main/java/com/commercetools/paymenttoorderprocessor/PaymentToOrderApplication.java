package com.commercetools.paymenttoorderprocessor;

import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.client.SphereAccessTokenSupplier;
import io.sphere.sdk.client.SphereClient;
import io.sphere.sdk.client.SphereClientFactory;
import io.sphere.sdk.http.HttpClient;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.util.concurrent.TimeUnit;

@Configuration
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class PaymentToOrderApplication {

    public static void main(String[] args) {
        bridgeJULToSLF4J();
        SpringApplication.run(PaymentToOrderApplication.class, args).close();
    }

    @Bean
    public HttpClient httpClient() {
        return SphereClientFactory.of().createHttpClient();
    }

    @Bean
    @DependsOn({"shereClientConfiguration", "httpClient"})
    public BlockingSphereClient blockingSphereClient(final ShereClientConfiguration config, final HttpClient httpClient) {
        final io.sphere.sdk.client.SphereClientConfig clientConfig = io.sphere.sdk.client.SphereClientConfig.of(config.getProjectKey(), config.getClientId(), config.getClientSecret());
        final SphereAccessTokenSupplier sphereAccessTokenSupplierWithAutoRefresh = SphereAccessTokenSupplier.ofAutoRefresh(clientConfig, httpClient, false);
        //lightweight client
        final SphereClient sphereClient = SphereClient.of(clientConfig, httpClient, sphereAccessTokenSupplierWithAutoRefresh);

        return BlockingSphereClient.of(sphereClient, config.getDefaultTimeout(), TimeUnit.MILLISECONDS);
    }

    /**
     * Routes all incoming j.u.l. (java.util.logging.Logger) records to the SLF4j API. This is done by:
     * <ol>
     *     <li>Removing existing handlers attached to the j.u.l root logger.</li>
     *     <li>Adding SLF4JBridgeHandler to j.u.l's root logger.</li>
     * </ol>
     * <p>Why we do the routing?
     * <p>Some dependencies (e.g. org.javamoney.moneta's DefaultMonetaryContextFactory) log events using the
     * j.u.l. This causes such logs to ignore the logback.xml configuration which is only
     * applied to logs from the SLF4j implementation.
     *
     */
    private static void bridgeJULToSLF4J() {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }
}
