package com.commercetools.paymenttoorderprocessor.utils;



import static com.commercetools.paymenttoorderprocessor.utils.CtpQueryUtils.queryAll;
import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.sphere.sdk.categories.Category;
import io.sphere.sdk.categories.queries.CategoryQuery;
import io.sphere.sdk.categories.queries.CategoryQueryModel;
import io.sphere.sdk.client.SphereClient;
import io.sphere.sdk.queries.PagedQueryResult;
import io.sphere.sdk.queries.QueryPredicate;
import io.sphere.sdk.queries.QuerySort;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
 public class CtpQueryUtilsTest {
    @Captor private ArgumentCaptor<CategoryQuery> sphereRequestArgumentCaptor;

    @Test
    public   void queryAll_WithCategoryKeyQuery_ShouldFetchCorrectCategories() {
        // preparation
        final SphereClient sphereClient = mock(SphereClient.class);
        final List<Category> categories = getMockCategoryPage();
        final PagedQueryResult mockQueryResults = getMockQueryResults(categories);
        when(sphereClient.execute(any())).thenReturn(completedFuture(mockQueryResults));

        final List<String> keysToQuery =
                IntStream.range(1, 510).mapToObj(i -> "key" + i).collect(Collectors.toList());

        final Function<CategoryQueryModel, QueryPredicate<Category>> keyPredicateFunction =
                categoryQueryModel -> categoryQueryModel.key().isIn(keysToQuery);

        // test
        queryAll(sphereClient, CategoryQuery.of().plusPredicates(keyPredicateFunction), identity())
                .toCompletableFuture()
                .join();

        // assertions
        verify(sphereClient, times(2)).execute(sphereRequestArgumentCaptor.capture());
        assertThat(sphereRequestArgumentCaptor.getAllValues())
                .containsExactly(
                        getFirstPageQuery(keyPredicateFunction, QuerySort.of("id asc")),
                        getSecondPageQuery(keyPredicateFunction, categories, QuerySort.of("id asc")));
    }

    @Test
   public void queryAll_WithCustomSort_ShouldNotSortByIdAsc() {
        // preparation
        final SphereClient sphereClient = mock(SphereClient.class);
        final List<Category> categories = getMockCategoryPage();
        final PagedQueryResult mockQueryResults = getMockQueryResults(categories);
        when(sphereClient.execute(any())).thenReturn(completedFuture(mockQueryResults));

        final List<String> keysToQuery =
                IntStream.range(1, 510).mapToObj(i -> "key" + i).collect(Collectors.toList());

        final Function<CategoryQueryModel, QueryPredicate<Category>> keyPredicateFunction =
                categoryQueryModel -> categoryQueryModel.key().isIn(keysToQuery);

        // test
        queryAll(
                sphereClient,
                CategoryQuery.of()
                        .withSort(QuerySort.of("id desc"))
                        .plusPredicates(keyPredicateFunction),
                identity())
                .toCompletableFuture()
                .join();

        // assertions
        verify(sphereClient, times(2)).execute(sphereRequestArgumentCaptor.capture());
        assertThat(sphereRequestArgumentCaptor.getAllValues())
                .containsExactly(
                        getFirstPageQuery(keyPredicateFunction, QuerySort.of("id desc")),
                        getSecondPageQuery(keyPredicateFunction, categories, QuerySort.of("id desc")));
    }




    @Nonnull
    private List<Category> getMockCategoryPage() {
        final List<Category> categories =
                IntStream.range(0, 500).mapToObj(i -> mock(Category.class)).collect(Collectors.toList());

        // stub an id to the last category in the page.
        final Category lastCategory = categories.get(categories.size() - 1);
        when(lastCategory.getId()).thenReturn(UUID.randomUUID().toString());
        return categories;
    }

    @Nonnull
    private PagedQueryResult getMockQueryResults(@Nonnull final List<Category> mockPage) {
        final PagedQueryResult pagedQueryResult = mock(PagedQueryResult.class);
        when(pagedQueryResult.getResults())
                .thenReturn(mockPage) // get full page on first call
                .thenReturn(mockPage.subList(0, 10)); // get only 10 categories on second call
        return pagedQueryResult;
    }

    @Nonnull
    private CategoryQuery getFirstPageQuery(
            @Nonnull final Function<CategoryQueryModel, QueryPredicate<Category>> keyPredicate,
            @Nonnull final QuerySort<Category> sort) {

        return CategoryQuery.of()
                .plusPredicates(keyPredicate)
                .withSort(sort)
                .withLimit(500)
                .withFetchTotal(false);
    }

    @Nonnull
    private CategoryQuery getSecondPageQuery(
            @Nonnull final Function<CategoryQueryModel, QueryPredicate<Category>> keyPredicateFunction,
            @Nonnull final List<Category> categoryPage,
            @Nonnull final QuerySort<Category> sort) {

        final String lastCategoryIdInPage = categoryPage.get(categoryPage.size() - 1).getId();

        return CategoryQuery.of()
                .plusPredicates(keyPredicateFunction)
                .withSort(sort)
                .plusPredicates(QueryPredicate.of(format("id > \"%s\"", lastCategoryIdInPage)))
                .withLimit(500)
                .withFetchTotal(false);
    }
}
