package com.commercetools.paymenttoorderprocessor.jobs.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import io.sphere.sdk.carts.Cart;
import io.sphere.sdk.http.HttpClient;
import io.sphere.sdk.http.HttpHeaders;
import io.sphere.sdk.http.HttpMethod;
import io.sphere.sdk.http.HttpRequest;
import io.sphere.sdk.http.HttpRequestBody;
import io.sphere.sdk.http.HttpResponse;
import io.sphere.sdk.http.NameValuePair;
import io.sphere.sdk.http.StringHttpRequestBody;
import io.sphere.sdk.json.SphereJsonUtils;

/***
 * Calls configured OrderCreation-Endpoint and sends encrypted Cart Json as Body 
 * @author mht@dotsource.de
 *
 */
public class MessageWriter implements ItemWriter<Cart> {
    public static final Logger LOG = LoggerFactory.getLogger(MessageWriter.class);

    private static final String ENCRYPTIONALGORITHM = "Blowfish";

    @Value("${createorder.encryptionkey}")
    private String encryptionKey;
    @Value("${createorder.endpoint.url}")
    private String urlstring;
    
    @Override
    public void write(List<? extends Cart> items) throws Exception {
        for (Cart item : items) {
            sendRequestToCreateOrder(item);
        }
    }

    private void sendRequestToCreateOrder(Cart cart) throws Exception {
        final String body = SphereJsonUtils.toJsonString(cart);
        final String bodyEncrypt = encrypt(body);
        final List<NameValuePair> headerList = new ArrayList<NameValuePair>();
        headerList.add(NameValuePair.of(HttpHeaders.CONTENT_TYPE,"text/plain"));
        headerList.add(NameValuePair.of("Content-Length", String.valueOf(bodyEncrypt.length())));
        final HttpHeaders httpHeader = HttpHeaders.of(headerList);
        final HttpRequestBody httpBody = StringHttpRequestBody.of(bodyEncrypt);
        final HttpRequest httpRequest = HttpRequest.of(HttpMethod.POST, urlstring, httpHeader, httpBody);
        final HttpResponse httpResponse = httpClient.execute(httpRequest).toCompletableFuture().get(20000, TimeUnit.MILLISECONDS);
        final Integer statusCode = httpResponse.getStatusCode();
        if (statusCode != 200) {
            LOG.warn("Got Http-StatusCode {} from CreateOrder Endpoint for Cart {}", statusCode, cart);
        }
    }

    @Autowired
    HttpClient httpClient;

    private String encrypt(String value) {
        try {
            byte[] keyData = encryptionKey.getBytes();
            SecretKeySpec ks = new SecretKeySpec(keyData, ENCRYPTIONALGORITHM);
            Cipher cipher = Cipher.getInstance(ENCRYPTIONALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, ks);
            byte[] encrypted = cipher.doFinal(value.getBytes());
            return Base64.encodeBase64String(encrypted);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }
}
