package com.ferwin.transfers.ledger;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Asiento contable inmutable: el historial se reconstruye sumando asientos, nunca editándolos. */
@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "transfer_id", nullable = false)
	private UUID transferId;

	@Column(name = "account_id", nullable = false)
	private Long accountId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Direction direction;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
	private BigDecimal balanceAfter;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected LedgerEntry() {
	}

	public LedgerEntry(UUID transferId, Long accountId, Direction direction, BigDecimal amount, BigDecimal balanceAfter,
			OffsetDateTime createdAt) {
		this.transferId = transferId;
		this.accountId = accountId;
		this.direction = direction;
		this.amount = amount;
		this.balanceAfter = balanceAfter;
		this.createdAt = createdAt;
	}

	public enum Direction {
		DEBIT,
		CREDIT
	}

	/** Monto con signo: negativo para débitos, positivo para créditos. */
	public BigDecimal signedAmount() {
		return direction == Direction.DEBIT ? amount.negate() : amount;
	}

	public Long getId() {
		return id;
	}

	public UUID getTransferId() {
		return transferId;
	}

	public Long getAccountId() {
		return accountId;
	}

	public Direction getDirection() {
		return direction;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public BigDecimal getBalanceAfter() {
		return balanceAfter;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}
}
