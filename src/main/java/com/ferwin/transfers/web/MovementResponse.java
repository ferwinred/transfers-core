package com.ferwin.transfers.web;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.ferwin.transfers.ledger.LedgerEntry;

public record MovementResponse(UUID transferId, String direction, BigDecimal amount, BigDecimal balanceAfter,
		OffsetDateTime createdAt) {

	public static MovementResponse from(LedgerEntry entry) {
		return new MovementResponse(entry.getTransferId(), entry.getDirection().name(), entry.getAmount(),
				entry.getBalanceAfter(), entry.getCreatedAt());
	}
}
