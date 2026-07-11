package com.app.transfer.adapters.youtube;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchQuotaRepository extends JpaRepository<SearchQuotaEntry, java.time.LocalDate> {
}