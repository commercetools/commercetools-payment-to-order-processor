package com.commercetools.paymenttoorderprocessor.fixtures;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.UnaryOperator;

import com.commercetools.paymenttoorderprocessor.timestamp.TimeStamp;

import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.customobjects.CustomObject;
import io.sphere.sdk.customobjects.CustomObjectDraft;
import io.sphere.sdk.customobjects.commands.CustomObjectDeleteCommand;
import io.sphere.sdk.customobjects.commands.CustomObjectUpsertCommand;
import io.sphere.sdk.customobjects.queries.CustomObjectQuery;

public class TimeStampFixtures {
    public static void withTimeStamp(final BlockingSphereClient client, final String container, final UnaryOperator<TimeStamp> op) {
        final TimeStamp timeStamp = new TimeStamp(ZonedDateTime.now()); 
        final CustomObjectDraft<TimeStamp> draft = CustomObjectDraft.ofUnversionedUpsert(container, "lastUpdated" ,timeStamp, TimeStamp.class);
        final CustomObjectUpsertCommand<TimeStamp> updateCommad = CustomObjectUpsertCommand.of(draft);
        CustomObject<TimeStamp> timeStampCustomObject= client.executeBlocking(updateCommad);
        TimeStamp newTimestamp = op.apply(timeStampCustomObject.getValue());
    }
    
    public static void removeTimeStamps(final BlockingSphereClient client, final String container) {
        final List<CustomObject<TimeStamp>> results = client.executeBlocking(
                CustomObjectQuery.of(TimeStamp.class).byContainer(container).plusPredicates(m->m.key().is("lastUpdated"))).getResults();
        for (CustomObject<TimeStamp> customObject : results) {
            client.executeBlocking(CustomObjectDeleteCommand.of(customObject, TimeStamp.class));
        }
    }
}
