package com.commercetools.paymenttoorderprocessor.jobs.actions;

import com.commercetools.paymenttoorderprocessor.timestamp.TimeStampManager;
import com.commercetools.paymenttoorderprocessor.wrapper.CartAndMessage;
import io.netty.handler.codec.http.QueryStringEncoder;
import io.sphere.sdk.carts.Cart;
import io.sphere.sdk.http.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.apache.commons.lang3.StringUtils.isNoneEmpty;

/***
 * Calls configured OrderCreation-Endpoint and sends Cart ID Json as Body
 * @author mht@dotsource.de
 *
 */
public class OrderCreator implements ItemWriter<CartAndMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(OrderCreator.class);

    /**
     * Must be without trailing slash.
     */
    @Value("${createorder.endpoint.url}")
    private String urlstring;

    /**
     * Optional basic HTTP authentication credentials if required by API endpoint {@code urlstring}.
     * <p>Value format is: <b>login:password</b></p>
     */
    @Value("${createorder.endpoint.authentication:#{null}}")
    private String authentication;

    @Autowired
    HttpClient httpClient;

    @Autowired
    TimeStampManager timeStampManager;

    /**
     * Milliseconds to wait create order API response.
     */
    @Value("${ctp.createOrderApi.timeout:40000}")
    private Integer createOrderTimeout;

    /**
     * Max number of character to log from API response body.
     * <p>
     * <b>Default</b>: 500 symbols.
     */
    @Value("${createorder.response.loggingLengthLimit:500}")
    private Integer responseBodyLogLimit;

    @Override
    public void write(List<? extends CartAndMessage> items) {
        for (CartAndMessage item : items) {
            sendRequestToCreateOrder(item);
        }
    }

    private void sendRequestToCreateOrder(CartAndMessage cartAndMessage) {
        final Cart cart = cartAndMessage.getCart();

        final String cartId = cart.getId();
        if (cartId == null) {
            timeStampManager.processingMessageFailed();
            return;
        }

        QueryStringEncoder queryStringEncoder = new QueryStringEncoder(urlstring);
        queryStringEncoder.addParam("cartId", cartId);

        HttpHeaders httpHeaders = HttpHeaders.empty();

        if (isNoneEmpty(authentication)) {
            String encoded = Base64.encodeBase64String(authentication.getBytes());
            httpHeaders = httpHeaders.plus(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        }

        final HttpRequest httpRequest = HttpRequest.of(HttpMethod.GET, queryStringEncoder.toString(), httpHeaders, null);

        HttpResponse httpResponse;
        try {
            //sending cart ID to API
            httpResponse = httpClient.execute(httpRequest).toCompletableFuture()
                    .get(createOrderTimeout, TimeUnit.MILLISECONDS);

            String responseBodyToLog = getResponseBodyToLog(httpResponse.getResponseBody());

            if (httpResponse.hasSuccessResponseCode()) {
                if (ObjectUtils.compare(httpResponse.getStatusCode(), HttpStatusCode.CREATED_201) == 0) {
                    // normal case: cart is created successfully
                    LOG.info("Success with status {}. Processed cart id=[{}], created order id=[{}]",
                            httpResponse.getStatusCode(), cartAndMessage.getCart().getId(), responseBodyToLog);
                } else {
                    // the request is finished successfully, but this cart can't be processed.
                    // this case should be reported, but not re-tried any more
                    LOG.info("Request is successful with status {}, but order is not created. "
                                    + "Cart [{}] is not processed. Reason: {} ",
                            httpResponse.getStatusCode(), cartAndMessage.getCart().getId(), responseBodyToLog);
                }
            } else {
                // request is not successful: should be re-tried later
                timeStampManager.processingMessageFailed();
                LOG.error("Failed with status {}. Reason: {}", httpResponse.getStatusCode(), responseBodyToLog);
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
     * @param e             Throwable to log.
     * @param exceptionType Internal custom exception type just to distinct in the logs different reasons
     *                      (like, "HTTP" exception, or "encryption" and so on)
     * @param urlstring     last called URL which request caused the exception.
     * @param cart          {@link Cart} which processing failed
     */
    private void logErrorAndFailTimestamp(Throwable e, String exceptionType, String urlstring, Cart cart) {
        LOG.error("Caught {} exception while calling Shop URL {} to create Order from Cart {}. Exception message: [{}]",
                exceptionType, urlstring, cart.getId(), e.toString());
        timeStampManager.processingMessageFailed();
    }

    /**
     * Format response body for logging:<ul>
     * <li>convert <i>byte</i> to {@link String} if possible</li>
     * <li>if necessary limit String length to {@link #responseBodyLogLimit} adding "..." to the end</li>
     * </ul>
     * <p>
     * So, this function returns a string with length up to <i>responseBodyLogLimit + 3</i> characters.
     *
     * @param responseBody byte array to convert to string and output
     * @return converted and truncated (if necessary) string from the {@code responseBody}
     */
    private String getResponseBodyToLog(byte[] responseBody) {
        String stringFromResponseBody = getStringFromResponseBody(responseBody);

        if (stringFromResponseBody.length() > responseBodyLogLimit) {
            stringFromResponseBody = StringUtils.left(stringFromResponseBody, responseBodyLogLimit) + "...";
        }

        return stringFromResponseBody;
    }

    /**
     * Converts {@code responseBody} byte array to String value
     *
     * @param responseBody value to convert
     * @return converting result if {@code responseBody} exists, "<<empty>>" string value otherwise.
     */
    private static String getStringFromResponseBody(byte[] responseBody) {
        return Optional.ofNullable(responseBody)
                .map(String::new)
                .orElse("<<empty>>");
    }
}
