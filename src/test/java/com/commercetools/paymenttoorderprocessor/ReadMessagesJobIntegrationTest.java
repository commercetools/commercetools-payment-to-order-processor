package com.commercetools.paymenttoorderprocessor;

import com.commercetools.paymenttoorderprocessor.customobjects.MessageProcessedManager;
import com.commercetools.paymenttoorderprocessor.fixtures.CartFixtures;
import com.commercetools.paymenttoorderprocessor.fixtures.PaymentFixtures;
import com.commercetools.paymenttoorderprocessor.jobs.ReadMessagesJob;
import com.commercetools.paymenttoorderprocessor.testconfiguration.BasicTestConfiguration;
import com.commercetools.paymenttoorderprocessor.testconfiguration.ExtendedTestConfiguration;
import com.commercetools.paymenttoorderprocessor.testconfiguration.HttpClientMock;
import com.commercetools.paymenttoorderprocessor.testconfiguration.HttpClientMockConfiguration;
import com.neovisionaries.i18n.CountryCode;
import io.sphere.sdk.carts.Cart;
import io.sphere.sdk.carts.CartDraft;
import io.sphere.sdk.carts.CustomLineItemDraft;
import io.sphere.sdk.carts.commands.CartDeleteCommand;
import io.sphere.sdk.carts.commands.CartUpdateCommand;
import io.sphere.sdk.carts.commands.updateactions.AddPayment;
import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.http.HttpStatusCode;
import io.sphere.sdk.messages.Message;
import io.sphere.sdk.messages.queries.MessageQuery;
import io.sphere.sdk.models.Address;
import io.sphere.sdk.models.LocalizedString;
import io.sphere.sdk.payments.*;
import io.sphere.sdk.payments.commands.PaymentUpdateCommand;
import io.sphere.sdk.payments.commands.updateactions.AddTransaction;
import io.sphere.sdk.payments.commands.updateactions.ChangeTransactionState;
import io.sphere.sdk.projects.Project;
import io.sphere.sdk.projects.queries.ProjectGet;
import io.sphere.sdk.queries.PagedQueryResult;
import io.sphere.sdk.taxcategories.TaxCategory;
import io.sphere.sdk.taxcategories.queries.TaxCategoryQuery;
import org.assertj.core.api.Fail;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.commercetools.paymenttoorderprocessor.fixtures.PaymentFixtures.EUR;
import static com.commercetools.paymenttoorderprocessor.fixtures.PaymentFixtures.EUR_20;
import static org.assertj.core.api.Assertions.assertThat;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {ReadMessagesJob.class, BasicTestConfiguration.class, ExtendedTestConfiguration.class,
        ShereClientConfiguration.class, MessageReaderIntegrationTest.ContextConfiguration.class,
        HttpClientMockConfiguration.class},
        initializers = ConfigFileApplicationContextInitializer.class,
        loader = SpringBootContextLoader.class)
public class ReadMessagesJobIntegrationTest extends IntegrationTest {

    @Autowired
    private BlockingSphereClient testClient;

    @Autowired
    private Job job;

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private HttpClientMock httpClientMock;

    @Autowired
    private MessageProcessedManager messageProcessedManager;

    @Value("${ctp.custom.object.containername}")
    private String customObjectContainerName;

    @Test
    public void readAndProcessMessagesJobIntegrationTest() throws Exception {
        PaymentFixtures.withPayments(2, testClient, payments -> {
            // Prepare:
            ZonedDateTime now = ZonedDateTime.now();

            final List<TaxCategory> results = checkPreconditions();

            httpClientMock.spyStatusCode(HttpStatusCode.OK_200);

            Payment payment = payments.get(0);
            final CustomLineItemDraft customLineItemDraft = CustomLineItemDraft.of(LocalizedString.ofEnglish("messageProcessorIntegrationTestCustomLineItem"), "Slug", EUR_20, results.get(0), 1L);
            final Address address = Address.of(CountryCode.DE);
            final Cart cart = CartFixtures.createCart(testClient, CartDraft.of(EUR)
                    .withCustomLineItems(Collections.singletonList(customLineItemDraft))
                    .withShippingAddress(address));
            final Cart cartUpdated = testClient.executeBlocking(CartUpdateCommand.of(cart, AddPayment.of(payment)));

            final TransactionDraft transactionDraft = TransactionDraftBuilder.of(TransactionType.AUTHORIZATION, EUR_20).state(TransactionState.INITIAL).build();
            final AddTransaction addTransaction = AddTransaction.of(transactionDraft);
            final Payment paymentWithTransaction = testClient.executeBlocking(PaymentUpdateCommand.of(payment, addTransaction));

            final Transaction transaction = paymentWithTransaction.getTransactions().get(0);
            final ChangeTransactionState changeTransactionState = ChangeTransactionState.of(TransactionState.SUCCESS, transaction.getId());
            final Payment paymentWithTransactionStateChange = testClient.executeBlocking(PaymentUpdateCommand.of(paymentWithTransaction, changeTransactionState));

            Payment payment2 = payments.get(1);

            final Cart cart2 = CartFixtures.createCart(testClient, CartDraft.of(EUR)
                    .withCustomLineItems(Collections.singletonList(customLineItemDraft))
                    .withShippingAddress(address));
            final Cart cart2Updated = testClient.executeBlocking(CartUpdateCommand.of(cart2, AddPayment.of(payment2)));

            final TransactionDraft transactionDraft2 = TransactionDraftBuilder.of(TransactionType.CHARGE, EUR_20).state(TransactionState.SUCCESS).build();
            final AddTransaction addTransaction2 = AddTransaction.of(transactionDraft2);
            final Payment payment2WithTransactionAdded =  testClient.executeBlocking(PaymentUpdateCommand.of(payment2, addTransaction2));

            // Test:
            try {
                //Give Platform time to create messages
                Thread.sleep(10000L);
                jobLauncher.run(job, new JobParameters());
            } catch (Exception e) {
                Fail.fail("Unexpected exception when testing", e);
            }

            // Assert:
            List<Message> messages = fetchMessagesFrom(now);
            for (Message message : messages) {
                assertThat(messageProcessedManager.isMessageUnprocessed(message)).isFalse();
            }

            // Clean up:
            deleteCarts(Arrays.asList(cartUpdated, cart2Updated));
            return Arrays.asList(paymentWithTransactionStateChange, payment2WithTransactionAdded);
        });
    }

    private void deleteCarts(List<Cart> carts) {
        for (Cart cart : carts) {
            testClient.executeBlocking(CartDeleteCommand.of(cart));
        }
    }

    private List<Message> fetchMessagesFrom(ZonedDateTime now) {
        MessageQuery messageQuery = MessageQuery.of()
                .plusPredicates(m -> m.type().is("PaymentTransactionAdded").or(m.type().is("PaymentTransactionStateChanged")))
                .plusPredicates(m -> m.lastModifiedAt().isGreaterThan(now));
        PagedQueryResult<Message> messagePagedQueryResult = testClient.executeBlocking(messageQuery);
        return messagePagedQueryResult.getResults();
    }

    private List<TaxCategory> checkPreconditions() {
        final Project project = testClient.executeBlocking(ProjectGet.of());
        final Boolean isMessageEnabled = project.getMessages().isEnabled();
        assertThat(isMessageEnabled).as("Project should have messages enabled.").isTrue();
        final PagedQueryResult<TaxCategory> result = testClient.executeBlocking(TaxCategoryQuery.of().byName("standard"));
        final List<TaxCategory> results = result.getResults();
        assertThat(results).isNotEmpty();
        return results;
    }
}