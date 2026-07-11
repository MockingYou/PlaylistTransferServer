package com.app.transfer.adapters.youtube;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
public class SearchQuotaEntry {
    @Id
    private LocalDate date; // one row per day
    private int searchesUsed;
}