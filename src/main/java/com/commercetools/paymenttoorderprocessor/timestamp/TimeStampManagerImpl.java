package com.commercetools.paymenttoorderprocessor.timestamp;

import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.customobjects.CustomObject;
import io.sphere.sdk.customobjects.CustomObjectDraft;
import io.sphere.sdk.customobjects.commands.CustomObjectUpsertCommand;
import io.sphere.sdk.customobjects.queries.CustomObjectQuery;
import io.sphere.sdk.queries.PagedQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Handles (read and save) the Custom-Object TimeStamp in the commercetools platform.
 *
 * @author mht@dotsource.de
 */
public class TimeStampManagerImpl implements TimeStampManager {

    public static final Logger LOG = LoggerFactory.getLogger(TimeStampManagerImpl.class);

    public TimeStampManagerImpl() {
    }

    //for Unittest
    public TimeStampManagerImpl(final String containerName, final BlockingSphereClient client) {
        this.containerName = containerName;
        this.client = client;
    }

    private final String KEY = "lastUpdated";
    @Autowired
    BlockingSphereClient client;

    @Value("${ctp.custom.object.containername}")
    private String containerName;

    @Nullable
    private CustomObject<TimeStamp> lastTimestamp = null;

    private boolean wasTimeStampQueried = false;
    private ZonedDateTime lastActualProcessedMessageTimeStamp;
    private boolean processingMessageFailed = false;

    @Override
    public ZonedDateTime getLastProcessedMessageTimeStamp() {
        if (!wasTimeStampQueried) {
            queryTimeStamp();
        }

        return lastTimestamp != null ? lastTimestamp.getValue().getLastTimeStamp() : null;
    }

    @Override
    public void setActualProcessedMessageTimeStamp(final ZonedDateTime timeStamp) {
        if (!processingMessageFailed) {
            this.lastActualProcessedMessageTimeStamp = timeStamp;
        }
    }

    @Override
    public void persistLastProcessedMessageTimeStamp() {
        if (lastActualProcessedMessageTimeStamp != null) {
            final CustomObjectDraft<TimeStamp> draft = createCustomObjectDraft();
            final CustomObjectUpsertCommand<TimeStamp> updateCommad = CustomObjectUpsertCommand.of(draft);
            client.executeBlocking(updateCommad);
            LOG.info("Set new last processed timestamp: {}", lastActualProcessedMessageTimeStamp.toString());
        } else {
            LOG.info("No one message was processed - lastTimestamp is unchanged: [{}]",
                timestampToString(lastTimestamp));
        }
    }

    @Override
    public void processingMessageFailed() {
        processingMessageFailed = true;
    }

    private void queryTimeStamp() {
        final CustomObjectQuery<TimeStamp> customObjectQuery = CustomObjectQuery.of(TimeStamp.class)
                .byContainer(containerName)
                .plusPredicates(co -> co.key().is(KEY));
        final PagedQueryResult<CustomObject<TimeStamp>> result = client.executeBlocking(customObjectQuery);
        final List<CustomObject<TimeStamp>> results = result.getResults();
        if (results.isEmpty()) {
            LOG.warn("No Timestamp for last processed message for was found at commercetools platform. This should only happen on the first run.");
        } else {
            lastTimestamp = results.get(0);
            LOG.info("Got Timestamp from commercetools platform: [{}] ", timestampToString(lastTimestamp));
        }
        wasTimeStampQueried = true;
    }

    private CustomObjectDraft<TimeStamp> createCustomObjectDraft() {
        final TimeStamp timeStamp = new TimeStamp(lastActualProcessedMessageTimeStamp);
        if (lastTimestamp != null) {
            return CustomObjectDraft.ofVersionedUpdate(lastTimestamp, timeStamp, TimeStamp.class);
        } else {
            return CustomObjectDraft.ofUnversionedUpsert(containerName, KEY, timeStamp, TimeStamp.class);
        }
    }

    @Nonnull
    private static String timestampToString(CustomObject<TimeStamp> timestamp) {
      return Optional.ofNullable(timestamp)
          .map(CustomObject::getValue)
          .map(TimeStamp::getLastTimeStamp)
          .map(Object::toString)
          .orElse("null");
    }
}
