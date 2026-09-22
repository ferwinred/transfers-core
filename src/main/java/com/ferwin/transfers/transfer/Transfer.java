package com.ferwin.transfers.transfer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transfer")
public class Transfer {

	@Id
	private UUID id;

	@Column(name = "idempotency_key", nullable = false, length = 64)
	private String idempotencyKey;

	@Column(name = "requested_by", nullable = false)
	private String requestedBy;

	/** SHA-256 del cuerpo: permite detectar que la misma llave se reutilizó con otros datos. */
	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Column(name = "source_account", nullable = false)
	private Long sourceAccountId;

	@Column(name = "target_account", nullable = false)
	private Long targetAccountId;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, length = 3)
	private String currency;

	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TransferStatus status;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected Transfer() {
	}

	public Transfer(String idempotencyKey, String requestedBy, String requestHash, Long sourceAccountId,
			Long targetAccountId, BigDecimal amount, String currency, String description) {
		this.id = UUID.randomUUID();
		this.idempotencyKey = idempotencyKey;
		this.requestedBy = requestedBy;
		this.requestHash = requestHash;
		this.sourceAccountId = sourceAccountId;
		this.targetAccountId = targetAccountId;
		this.amount = amount;
		this.currency = currency;
		this.description = description;
		this.status = TransferStatus.COMPLETED;
		this.createdAt = OffsetDateTime.now();
	}

	public UUID getId() {
		return id;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getRequestedBy() {
		return requestedBy;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public Long getSourceAccountId() {
		return sourceAccountId;
	}

	public Long getTargetAccountId() {
		return targetAccountId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getCurrency() {
		return currency;
	}

	public String getDescription() {
		return description;
	}

	public TransferStatus getStatus() {
		return status;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}
}
