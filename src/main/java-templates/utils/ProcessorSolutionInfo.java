package com.commercetools.paymenttoorderprocessor.utils;

import io.sphere.sdk.client.SolutionInfo;



public final class ProcessorSolutionInfo extends SolutionInfo {
    private static final String LIB_NAME = "commercetools-payment-to-order-processor";
    private String version = "${version}";

    /**
     * Extends {@link SolutionInfo} class of the JVM SDK to append to the User-Agent header with
     * information of the commercetools-sync-java library
     */
    public ProcessorSolutionInfo() {
        setName(LIB_NAME);
        setVersion(version);
    }
}
