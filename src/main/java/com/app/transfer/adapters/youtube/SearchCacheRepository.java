package com.app.transfer.adapters.youtube;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SearchCacheRepository extends JpaRepository<SearchCacheEntry, String> {
    Optional<SearchCacheEntry> findByNormalizedQuery(String normalizedQuery);
}