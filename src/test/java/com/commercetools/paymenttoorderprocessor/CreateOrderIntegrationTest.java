package com.commercetools.paymenttoorderprocessor;

import org.junit.Test;

public class CreateOrderIntegrationTest extends IntegrationTest {

    @Test
    public void createOrderIntegrationTest()  throws Exception {
        PaymentFixtures.withPayment(client(), payment-> {
            return payment;
        });
    }
}