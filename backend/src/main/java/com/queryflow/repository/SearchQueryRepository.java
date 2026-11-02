package com.queryflow.repository;

import com.queryflow.entity.SearchQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.Collection;
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

    @Query("SELECT s FROM SearchQuery s ORDER BY s.count DESC")
    List<SearchQuery> findTopTrending(Pageable pageable);

    List<SearchQuery> findByQueryIn(Collection<String> queries);

    /**
     * Atomically adds {@code delta} to a query's count in the database
     * ({@code UPDATE ... SET count = count + :delta}). Unlike load-modify-save, two
     * backend instances flushing the same query at the same time cannot overwrite
     * each other's increment: the row lock serialises them and both deltas land.
     *
     * @return rows updated: 1 if the query exists, 0 if it must be inserted
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SearchQuery s SET s.count = s.count + :delta, s.lastSearched = :now WHERE s.query = :query")
    int incrementCount(@Param("query") String query, @Param("delta") long delta, @Param("now") LocalDateTime now);
}

