package com.commercetools.paymenttoorderprocessor;

import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.client.SphereAccessTokenSupplier;
import io.sphere.sdk.client.SphereClient;
import io.sphere.sdk.client.SphereClientFactory;
import io.sphere.sdk.http.HttpClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.util.concurrent.TimeUnit;

@Configuration
@SpringBootApplication
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
public class PaymentToOrderApplication {

    public static void main(String[] args) {
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
}
