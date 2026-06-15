package com.queryflow.repository;

import com.queryflow.entity.SearchQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SearchQueryRepository extends JpaRepository<SearchQuery, Long> {
    
    Optional<SearchQuery> findByQuery(String query);

    @Query("SELECT s.query FROM SearchQuery s")
    List<String> findAllQueries();

    /**
     * Finds top 10 autocomplete suggestions matching a query prefix, sorted by popularity descending.
     * Generates a SQL query equivalent to:
     * WHERE LOWER(query) LIKE LOWER(prefix || '%') ORDER BY count DESC LIMIT 10
     */
    List<SearchQuery> findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc(String prefix);
}
