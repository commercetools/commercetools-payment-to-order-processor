package com.commercetools.paymenttoorderprocessor.testconfiguration;

import com.commercetools.paymenttoorderprocessor.ShereClientConfiguration;
import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.client.SphereAccessTokenSupplier;
import io.sphere.sdk.client.SphereClient;
import io.sphere.sdk.client.SphereClientFactory;
import io.sphere.sdk.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Configuration
public class BasicTestConfiguration {

    private HttpClient httpClient;

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertyConfigurer() {
       return new PropertySourcesPlaceholderConfigurer();
    }

    /**
     * Use this bean as a real http client.
     * @return
     */
    @Bean
    public HttpClient defaultHttpClient() {
        return httpClient;
    }

    /**
     * Use this bean if you need to spy http client responses. See {@link HttpClientMockConfiguration} and
     * {@link HttpClientMock}.
     * @return
     */
    @Bean
    public HttpClient httpClient() {
        return httpClient;
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

    @PostConstruct
    private void init() {
        httpClient = SphereClientFactory.of().createHttpClient();
    }
}
