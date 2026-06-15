package com.queryflow.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Recommendation for Database Indexing:
 * For optimal performance of autocomplete prefix queries under high load,
 * we recommend creating a functional index on the search_queries table:
 * 
 * CREATE INDEX idx_search_queries_query_lower ON search_queries(LOWER(query));
 * 
 * Why:
 * The suggestion engine runs case-insensitive queries using "LOWER(query) LIKE LOWER(prefix || '%')".
 * A standard B-Tree index on 'query' is not utilized during LOWER() calculations. Creating a
 * functional index on LOWER(query) ensures the database can perform an index scan, keeping
 * response latency minimal even with 100k+ records.
 */
@Entity
@Table(name = "search_queries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "search_queries_seq")
    @SequenceGenerator(name = "search_queries_seq", sequenceName = "search_queries_id_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, unique = true)
    private String query;

    @Column(nullable = false)
    @Builder.Default
    private Long count = 0L;

    @Column(name = "last_searched")
    private LocalDateTime lastSearched;
}
