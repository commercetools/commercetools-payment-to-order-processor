package com.commercetools.paymenttoorderprocessor.customobjects;

import com.heshammassoud.correlationiddecorator.Request;
import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.customobjects.CustomObject;
import io.sphere.sdk.customobjects.CustomObjectDraft;
import io.sphere.sdk.customobjects.commands.CustomObjectUpsertCommand;
import io.sphere.sdk.customobjects.queries.CustomObjectQuery;
import io.sphere.sdk.messages.Message;
import io.sphere.sdk.queries.PagedQueryResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

import static com.commercetools.paymenttoorderprocessor.utils.CorrelationIdUtil.getFromMDCOrGenerateNew;

/**
 * Reads custom objects in commercetools platform and checks if Message was already processed.
 *
 * @author mht@dotsource.de
 */
public class MessageProcessedManagerImpl implements MessageProcessedManager {

    @Autowired
    private BlockingSphereClient client;

    @Value("${ctp.custom.object.containername}")
    private String customObjectContainerName;

    private final static String PROCESSED = "processed";

    @Override
    public boolean isMessageUnprocessed(Message message) {
        final CustomObjectQuery<String> query = CustomObjectQuery.of(String.class)
                .byContainer(customObjectContainerName)
                .plusPredicates(co -> co.key().is(message.getId()));
        final PagedQueryResult<CustomObject<String>> result = client
            .executeBlocking(Request.of(query, getFromMDCOrGenerateNew()));
        final List<CustomObject<String>> results = result.getResults();
        if (results.isEmpty()) {
            return true;
        } else {
            //There should be can only be one Custom Object because Key/Container is unique
            assert (results.size() == 1);
            final CustomObject<String> customObject = results.get(0);
            return !PROCESSED.equals(customObject.getValue());
        }
    }

    @Override
    public void setMessageIsProcessed(Message message) {
        final CustomObjectDraft<String> draft = CustomObjectDraft
            .ofUnversionedUpsert(customObjectContainerName, message.getId(), PROCESSED, String.class);
        client.executeBlocking(
            Request.of(CustomObjectUpsertCommand.of(draft), getFromMDCOrGenerateNew())
        );
    }
}
