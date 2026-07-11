package com.app.transfer.adapters.youtube;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class SearchQuotaGuard {

    private static final int DAILY_SEARCH_LIMIT = 90; // stay safely under the ~100 cap

    private final SearchQuotaRepository repository;

    public synchronized boolean tryConsumeSearch() {
        LocalDate today = LocalDate.now();
        SearchQuotaEntry entry = repository.findById(today)
                .orElseGet(() -> {
                    SearchQuotaEntry fresh = new SearchQuotaEntry();
                    fresh.setDate(today);
                    fresh.setSearchesUsed(0);
                    return fresh;
                });

        if (entry.getSearchesUsed() >= DAILY_SEARCH_LIMIT) {
            return false;
        }

        entry.setSearchesUsed(entry.getSearchesUsed() + 1);
        repository.save(entry);
        return true;
    }
}