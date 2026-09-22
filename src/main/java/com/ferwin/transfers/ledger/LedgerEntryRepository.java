package com.ferwin.transfers.ledger;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

	List<LedgerEntry> findByAccountIdOrderByCreatedAtDescIdDesc(Long accountId, Pageable pageable);

	List<LedgerEntry> findByTransferId(UUID transferId);
}
