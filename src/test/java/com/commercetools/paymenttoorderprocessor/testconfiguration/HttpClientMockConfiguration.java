package com.commercetools.paymenttoorderprocessor.testconfiguration;

import com.commercetools.paymenttoorderprocessor.helper.CartAndMessageCreateHelper;
import io.sphere.sdk.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/**
 * Configuration to make a tests where spying of the responses is required.
 *
 * This config overrides <b>httpClient</b> bean with {@link HttpClientMock} instance. Inject <b>httpClientMock</b>
 * bean to spy responses.
 */
@Configuration
public class HttpClientMockConfiguration {

    @Bean
    public HttpClientMock httpClientMock() {
        return new HttpClientMock();
    }

    @Bean
    @DependsOn("httpClientMock")
    public HttpClient httpClient() {
        return httpClientMock().httpClient();
    }

    @Bean
    public CartAndMessageCreateHelper cartAndMessageCreateHelper() {
        return new CartAndMessageCreateHelper();
    }
}
