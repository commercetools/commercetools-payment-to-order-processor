package com.commercetools.paymenttoorderprocessor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ShereClientConfiguration {

    @Value("${ctp.credentials.clientid}")
    private String clientId;
    @Value("${ctp.credentials.clientsecret}")
    private String clientSecret;
    @Value("${ctp.credentials.projectkey}")
    private String projectKey;
    @Value("${ctp.timeout}")
    private Integer defaultTimeout;

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getProjectKey() {
        return projectKey;
    }
    public Integer getDefaultTimeout() {
        return defaultTimeout;
    }
    
}
