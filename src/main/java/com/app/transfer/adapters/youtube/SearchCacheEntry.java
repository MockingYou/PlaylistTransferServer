package com.app.transfer.adapters.youtube;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SearchCacheEntry {

    @Id
    private String normalizedQuery; // e.g. "blinding lights|the weeknd"

    private String youtubeVideoId;
    private Instant cachedAt;
}