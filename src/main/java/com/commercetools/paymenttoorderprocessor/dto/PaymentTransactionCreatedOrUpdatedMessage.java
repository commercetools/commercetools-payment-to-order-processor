package com.commercetools.paymenttoorderprocessor.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.sphere.sdk.messages.GenericMessageImpl;
import io.sphere.sdk.messages.MessageDerivateHint;
import io.sphere.sdk.messages.UserProvidedIdentifiers;
import io.sphere.sdk.payments.Payment;
import io.sphere.sdk.payments.Transaction;
import io.sphere.sdk.payments.TransactionState;

import java.time.ZonedDateTime;

@JsonDeserialize(
        as = PaymentTransactionCreatedOrUpdatedMessage.class
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentTransactionCreatedOrUpdatedMessage extends GenericMessageImpl<Payment> {
    private final TransactionState state;
    private final String transactionId;
    private final Transaction transaction;

    @JsonCreator()
    private PaymentTransactionCreatedOrUpdatedMessage(@JsonProperty("id") final String id,
                                                      @JsonProperty("version") final Long version,
                                                      @JsonProperty("createdAt") final ZonedDateTime createdAt,
                                                      @JsonProperty("lastModifiedAt") final ZonedDateTime lastModifiedAt,
                                                      @JsonProperty("resource") final JsonNode resource,
                                                      @JsonProperty("sequenceNumber") final Long sequenceNumber,
                                                      @JsonProperty("resourceVersion") final Long resourceVersion,
                                                      @JsonProperty("type") final String type,
                                                      @JsonProperty("resourceUserProvidedIdentifiers") final UserProvidedIdentifiers resourceUserProvidedIdentifiers,
                                                      @JsonProperty("state") final TransactionState state,
                                                      @JsonProperty("transactionId") final String transactionId,
                                                      @JsonProperty("transaction") final Transaction transaction) {
        super(id, version, createdAt, lastModifiedAt, resource, sequenceNumber, resourceVersion, type, resourceUserProvidedIdentifiers, Payment.class);
        this.state = state;
        this.transactionId = transactionId;
        this.transaction = transaction;
    }

    public TransactionState getState() {
        Transaction transaction = this.getTransaction();
        if (transaction != null) {
            return transaction.getState();
        }
        return this.state;
    }

    public String getTransactionId() {
        Transaction transaction = this.getTransaction();
        if (transaction != null) {
            return transaction.getId();
        }
        return this.transactionId;
    }

    public Transaction getTransaction() {
        return transaction;
    }
}
