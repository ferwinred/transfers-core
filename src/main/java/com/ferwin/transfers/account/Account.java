package com.ferwin.transfers.account;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "account")
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "account_number", nullable = false, unique = true, length = 20)
	private String accountNumber;

	@Column(name = "owner_name", nullable = false)
	private String ownerName;

	@Column(name = "owner_username", nullable = false)
	private String ownerUsername;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal balance;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AccountStatus status;

	/** Bloqueo optimista como segunda línea de defensa además del bloqueo pesimista. */
	@Version
	private long version;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected Account() {
	}

	public Account(String accountNumber, String ownerName, String ownerUsername, String currency, BigDecimal balance) {
		this.accountNumber = accountNumber;
		this.ownerName = ownerName;
		this.ownerUsername = ownerUsername;
		this.currency = currency;
		this.balance = balance;
		this.status = AccountStatus.ACTIVE;
		this.createdAt = OffsetDateTime.now();
	}

	public void debit(BigDecimal amount) {
		if (balance.compareTo(amount) < 0) {
			throw new IllegalStateException("Saldo insuficiente");
		}
		balance = balance.subtract(amount);
	}

	public void credit(BigDecimal amount) {
		balance = balance.add(amount);
	}

	public boolean isActive() {
		return status == AccountStatus.ACTIVE;
	}

	public void block() {
		status = AccountStatus.BLOCKED;
	}

	public Long getId() {
		return id;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public String getOwnerName() {
		return ownerName;
	}

	public String getOwnerUsername() {
		return ownerUsername;
	}

	public String getCurrency() {
		return currency;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public AccountStatus getStatus() {
		return status;
	}
}
