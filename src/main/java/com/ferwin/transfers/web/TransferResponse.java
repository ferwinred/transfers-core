package com.ferwin.transfers.web;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.ferwin.transfers.common.Masking;
import com.ferwin.transfers.transfer.Transfer;

/** Respuesta de una transferencia. La cuenta destino va enmascarada: no pertenece al usuario. */
public record TransferResponse(UUID id, String status, String sourceAccount, String targetAccount, BigDecimal amount,
		String currency, String description, OffsetDateTime createdAt) {

	public static TransferResponse from(Transfer transfer, String sourceNumber, String targetNumber) {
		return new TransferResponse(transfer.getId(), transfer.getStatus().name(), sourceNumber,
				Masking.accountNumber(targetNumber), transfer.getAmount(), transfer.getCurrency(),
				transfer.getDescription(), transfer.getCreatedAt());
	}
}
