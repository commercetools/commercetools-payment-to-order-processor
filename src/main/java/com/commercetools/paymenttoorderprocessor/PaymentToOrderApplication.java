package com.commercetools.paymenttoorderprocessor;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.client.SphereClient;
import io.sphere.sdk.client.SphereClientFactory;

@Configuration
@SpringBootApplication
public class PaymentToOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentToOrderApplication.class, args).close();
    }
    
    @Bean
    @DependsOn("shereClientConfiguration")
    public BlockingSphereClient blockingSphereClient(ShereClientConfiguration config) {
        final SphereClient sphereClient  = SphereClientFactory.of().createClient(config.getProjectKey(), config.getClientId(), config.getClientSecret());
        return BlockingSphereClient.of(sphereClient, config.getDefaultTimeout(), TimeUnit.MILLISECONDS);
    }
}
