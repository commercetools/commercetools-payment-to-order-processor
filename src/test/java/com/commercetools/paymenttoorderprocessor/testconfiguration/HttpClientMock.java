package com.commercetools.paymenttoorderprocessor.testconfiguration;

import io.sphere.sdk.http.HttpClient;
import io.sphere.sdk.http.HttpHeaders;
import io.sphere.sdk.http.HttpRequest;
import io.sphere.sdk.http.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Mock of injected {@link HttpClient} to spy response behavior if necessary.
 *
 * Since the application requests different resource (sphere client, order API) this client spies only those request,
 * which are ended to <b>createorder.endpoint.url</b>, all other requests are executes with real http client
 * (<i>defaultHttpClient</i>).
 *
 * Use <i>spy*</i> methods to set the next result of an http request.
 */
public class HttpClientMock {

    @Autowired
    private HttpClient defaultHttpClient;

    @Value("${createorder.endpoint.url}")
    private String endpointUrl;

    private Integer statusCode = 404;

    private HttpHeaders headers = null;

    private byte[] responseBody = new byte[0];

    public HttpClientMock spyStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public HttpClientMock spyHeaders(HttpHeaders headers) {
        this.headers = headers;
        return this;
    }

    public HttpClientMock spyResponseBody(byte[] responseBody) {
        this.responseBody = responseBody;
        return this;
    }

    public HttpClientMock spyResponse(Integer statusCode, String responseBody, HttpHeaders headers) {
        this.statusCode = statusCode;
        this.responseBody = Optional.ofNullable(responseBody).map(String::getBytes).orElse(null);
        this.headers = headers;
        return this;
    }

    public HttpClientMock spyResponse(Integer statusCode, String responseBody) {
        return spyResponse(statusCode, responseBody, null);
    }

    public HttpClientMock spyResponse(Integer statusCode) {
        return spyResponse(statusCode, null, null);
    }

    /**
     * Mocked http client to use in tests whey response spying is needed.
     *
     * This clients spies only responses to <i>createorder.endpoint.url</i>, for all other endpoints it makes real
     * HTTP request/responses.
     *
     * @return {@link HttpClient}
     */
    public HttpClient httpClient() {
        return new HttpClient() {
            @Override
            public CompletionStage<HttpResponse> execute(HttpRequest httpRequest) {

                if (httpRequest.getUrl().contains(endpointUrl)) {
                    return mockHttpResponseCompletableFuture(httpRequest);
                } else {
                    return defaultHttpClient.execute(httpRequest);
                }
            }

            @Override
            public void close() {
                // do nothing
            }
        };
    }

    private CompletableFuture<HttpResponse> mockHttpResponseCompletableFuture(final HttpRequest httpRequest) {
        return CompletableFuture.supplyAsync(() -> new HttpResponse() {
            @Nullable
            @Override
            public Integer getStatusCode() {
                return statusCode;
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }

            @Nullable
            @Override
            public byte[] getResponseBody() {
                return responseBody;
            }

            @Nullable
            @Override
            public HttpRequest getAssociatedRequest() {
                return httpRequest;
            }
        });
    }
}
