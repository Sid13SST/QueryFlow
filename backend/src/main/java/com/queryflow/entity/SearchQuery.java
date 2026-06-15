package com.queryflow.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

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
