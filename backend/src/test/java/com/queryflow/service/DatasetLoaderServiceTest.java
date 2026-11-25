package com.queryflow.service;

import com.queryflow.entity.SearchQuery;
import com.queryflow.repository.SearchQueryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CSV is loaded on every start-up (queryflow.dataset.load-on-startup). A hosted demo
 * restarts often (deploys, free-tier sleep), so loading must fill an empty database once and
 * then be a no-op that never duplicates rows or resets counts that real searches have changed.
 */
@DataJpaTest(properties = "spring.jpa.show-sql=false")
@Import(DatasetLoaderService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED) // each load commits, like separate start-ups
class DatasetLoaderServiceTest {

    /** Distinct valid rows in data/search_queries.csv (blank query, bad count and the repeat are skipped). */
    private static final long CSV_UNIQUE_ROWS = 11;

    @Autowired
    private DatasetLoaderService loader;

    @Autowired
    private SearchQueryRepository repository;

    @Test
    void firstStartFillsEmptyDatabaseAndRestartsAddNothing() {
        repository.deleteAll();

        loader.loadDataset();
        assertThat(repository.count()).isEqualTo(CSV_UNIQUE_ROWS);
        assertThat(repository.findByQuery("iphone")).map(SearchQuery::getCount).contains(100_000L);
        // First occurrence of a repeated query wins.
        assertThat(repository.findByQuery("duplicate_query")).map(SearchQuery::getCount).contains(100L);

        loader.loadDataset();
        loader.loadDataset();
        assertThat(repository.count()).isEqualTo(CSV_UNIQUE_ROWS);
    }

    @Test
    void restartKeepsCountsChangedByRealSearches() {
        repository.deleteAll();
        loader.loadDataset();

        SearchQuery java = repository.findByQuery("java").orElseThrow();
        java.setCount(java.getCount() + 42);
        repository.save(java);
        repository.save(SearchQuery.builder().query("typed by a visitor").count(3L).build());

        loader.loadDataset();

        assertThat(repository.findByQuery("java")).map(SearchQuery::getCount).contains(50_042L);
        assertThat(repository.findByQuery("typed by a visitor")).map(SearchQuery::getCount).contains(3L);
        assertThat(repository.count()).isEqualTo(CSV_UNIQUE_ROWS + 1);
    }
}
