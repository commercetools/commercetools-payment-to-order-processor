package com.commercetools.paymenttoorderprocessor.utils;

import io.sphere.sdk.client.SolutionInfo;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;

public final class ProcessorSolutionInfo extends SolutionInfo {
    private static final String LIB_NAME = "commercetools-payment-to-order-processor";
    public static final Logger LOG = LoggerFactory.getLogger(ProcessorSolutionInfo.class);
    private String version = "";

    /**
     * Extends {@link SolutionInfo} class of the JVM SDK to append to the User-Agent header with
     * information of the commercetools-sync-java library
     */
    public ProcessorSolutionInfo() {
        setName(LIB_NAME);
        if (StringUtils.isEmpty(version)) {
            MavenXpp3Reader reader = new MavenXpp3Reader();
            try {
                Model model = reader.read(new FileReader("pom.xml"));
                if (model != null) {
                    version = model.getVersion();
                }
            } catch (Exception e) {
                LOG.error("Cannot read pom.xml", e);
            }
        }
        setVersion(version);
    }
}
