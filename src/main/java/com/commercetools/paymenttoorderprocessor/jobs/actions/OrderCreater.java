package com.commercetools.paymenttoorderprocessor.jobs.actions;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.commercetools.paymenttoorderprocessor.customobjects.MessageProcessedManager;
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;
import com.commercetools.paymenttoorderprocessor.wrapper.CartAndMessage;

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
public class OrderCreater implements ItemWriter<CartAndMessage> {
    public static final Logger LOG = LoggerFactory.getLogger(OrderCreater.class);

    private static final String ENCRYPTIONALGORITHM = "Blowfish";

    @Value("${createorder.encryptionkey}")
    private String encryptionKey;
    @Value("${createorder.endpoint.url}")
    private String urlstring;

    @Autowired
    private MessageProcessedManager messageProcessedManager;

    @Autowired
    HttpClient httpClient;
    
    @Autowired
    TimeStampManager timeStampManager;

    @Override
    public void write(List<? extends CartAndMessage> items) throws Exception {
        for (CartAndMessage item : items) {
            sendRequestToCreateOrder(item);
        }
    }

    private void sendRequestToCreateOrder(CartAndMessage cartAndMessage) throws UnsupportedEncodingException{
        final Cart cart = cartAndMessage.getCart();
        LOG.info("Calling API to Create Order for Cart {}", cart);
        final String body = SphereJsonUtils.toJsonString(cart);
        final String bodyEncrypt = encrypt(body);
        final List<NameValuePair> headerList = new ArrayList<NameValuePair>();
        headerList.add(NameValuePair.of(HttpHeaders.CONTENT_TYPE,"text/plain"));
        headerList.add(NameValuePair.of("Content-Length", String.valueOf(bodyEncrypt.length())));
        final HttpHeaders httpHeader = HttpHeaders.of(headerList);
        final HttpRequestBody httpBody = StringHttpRequestBody.of(bodyEncrypt);
        final HttpRequest httpRequest = HttpRequest.of(HttpMethod.POST, urlstring, httpHeader, httpBody);
        HttpResponse httpResponse;
        try {
            httpResponse = httpClient.execute(httpRequest).toCompletableFuture().get(40000, TimeUnit.MILLISECONDS);
            if (httpResponse.hasSuccessResponseCode()) {
                LOG.info("Successfully called Shop API to create Order for Message {} and Cart {}", cartAndMessage.getMessage().getId(), cart.getId());
                messageProcessedManager.setMessageIsProcessed(cartAndMessage.getMessage());
            }
            else {
                LOG.warn("Response Code from API was {}", httpResponse.getStatusCode());
                messageProcessedManager.setMessageIsProcessed(cartAndMessage.getMessage());
            }
        }
        catch (InterruptedException | ExecutionException | TimeoutException e) {
            LOG.error("Caught exception {} while calling Shop URL {} to create Order from Cart {}", urlstring, cart.getId(), e.toString());
            //HTTP-Exception. Retry next time
            timeStampManager.processingMessageFailed();
        }

    }

    private String encrypt(String value) {
        try {
            byte[] keyData = encryptionKey.getBytes();
            SecretKeySpec ks = new SecretKeySpec(keyData, ENCRYPTIONALGORITHM);
            Cipher cipher = Cipher.getInstance(ENCRYPTIONALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, ks);
            byte[] encrypted = cipher.doFinal(value.getBytes());
            return Base64.encodeBase64String(encrypted);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            LOG.error("Encryption of http body failed. With Exception {}", e.toString());
        }

        return null;
    }
}
