package com.commercetools.paymenttoorderprocessor.jobs.actions;

import com.commercetools.paymenttoorderprocessor.customobjects.MessageProcessedManager;
import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;
import com.commercetools.paymenttoorderprocessor.wrapper.CartAndMessage;
import io.sphere.sdk.carts.Cart;
import io.sphere.sdk.http.*;
import io.sphere.sdk.json.SphereJsonUtils;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/***
 * Calls configured OrderCreation-Endpoint and sends encrypted Cart Json as Body
 * @author mht@dotsource.de
 *
 */
public class OrderCreator implements ItemWriter<CartAndMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(OrderCreator.class);

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

    /**
     * Milliseconds to wait create order API response.
     */
    @Value("${ctp.createOrderApi.timeout:40000}")
    private Integer createOrderTimeout;

    @Override
    public void write(List<? extends CartAndMessage> items) {
        for (CartAndMessage item : items) {
            sendRequestToCreateOrder(item);
        }
    }

    private void sendRequestToCreateOrder(CartAndMessage cartAndMessage) {
        final Cart cart = cartAndMessage.getCart();
        final String body = SphereJsonUtils.toJsonString(cart.getId());

        final String bodyEncrypt = encrypt(body);
        if (bodyEncrypt == null) {
            timeStampManager.processingMessageFailed();
            return;
        }
        final List<NameValuePair> headerList = new ArrayList<NameValuePair>();
        headerList.add(NameValuePair.of(HttpHeaders.CONTENT_TYPE, "text/plain"));
        headerList.add(NameValuePair.of("Content-Length", String.valueOf(bodyEncrypt.length())));
        final HttpHeaders httpHeader = HttpHeaders.of(headerList);
        final HttpRequestBody httpBody = StringHttpRequestBody.of(bodyEncrypt);
        final HttpRequest httpRequest = HttpRequest.of(HttpMethod.POST, urlstring, httpHeader, httpBody);
        HttpResponse httpResponse;
        try {
            //sending encrypted cart to API
            httpResponse = httpClient.execute(httpRequest).toCompletableFuture()
                    .get(createOrderTimeout, TimeUnit.MILLISECONDS);

            if (httpResponse.hasSuccessResponseCode()) {
                messageProcessedManager.setMessageIsProcessed(cartAndMessage.getMessage());
                LOG.info("Processed cart id=[{}], created order id=[{}]",
                        cartAndMessage.getCart().getId(), getStringFromResponseBody(httpResponse.getResponseBody()));
            } else {
                timeStampManager.processingMessageFailed();
                LOG.warn("Response Code from API was {}, response body: \"{}\"",
                        httpResponse.getStatusCode(), getStringFromResponseBody(httpResponse.getResponseBody()));
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logErrorAndFailTimestamp(e, "HTTP", urlstring, cart);
        } catch (Exception e) {
            logErrorAndFailTimestamp(e, "unexpected", urlstring, cart);
        }
    }

    /**
     * Log occurred exception mark the flow as failed, so the timestamp will not be updated (incremented) any more
     * (see {@link TimeStampManager#processingMessageFailed()}).
     *
     * @param e Throwable to log.
     * @param exceptionType Internal custom exception type just to distinct in the logs different reasons
     *                      (like, "HTTP" exception, or "encryption" and so on)
     * @param urlstring last called URL which request caused the exception.
     * @param cart {@link Cart} which processing failed
     */
    private void logErrorAndFailTimestamp(Throwable e, String exceptionType, String urlstring, Cart cart) {
        LOG.error("Caught {} exception while calling Shop URL {} to create Order from Cart {}. Exception message: [{}]",
                exceptionType, urlstring, cart.getId(), e.toString());
        timeStampManager.processingMessageFailed();
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

    /**
     * Converts {@code responseBody} byte array to String value
     * @param responseBody value to convert
     * @return converting result if {@code responseBody} exists, "<<empty>>" string value otherwise.
     */
    private static String getStringFromResponseBody(byte[] responseBody) {
        return Optional.ofNullable(responseBody)
                .map(String::new)
                .orElse("<<empty>>");
    }
}
