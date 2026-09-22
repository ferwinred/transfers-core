package com.ferwin.transfers.transfer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

	Optional<Transfer> findByRequestedByAndIdempotencyKey(String requestedBy, String idempotencyKey);

	@Query("select coalesce(sum(t.amount), 0) from Transfer t where t.sourceAccountId = :accountId and t.createdAt >= :since")
	BigDecimal sumOutgoingSince(@Param("accountId") Long accountId, @Param("since") OffsetDateTime since);
}
