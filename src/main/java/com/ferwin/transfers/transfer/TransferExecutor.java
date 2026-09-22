package com.ferwin.transfers.transfer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ferwin.transfers.account.Account;
import com.ferwin.transfers.account.AccountRef;
import com.ferwin.transfers.account.AccountRepository;
import com.ferwin.transfers.common.BusinessRuleException;
import com.ferwin.transfers.common.IdempotencyConflictException;
import com.ferwin.transfers.common.Masking;
import com.ferwin.transfers.common.NotFoundException;
import com.ferwin.transfers.ledger.LedgerEntry;
import com.ferwin.transfers.ledger.LedgerEntry.Direction;
import com.ferwin.transfers.ledger.LedgerEntryRepository;

/** Ejecuta una transferencia dentro de una única transacción de base de datos: todo o nada. */
@Component
class TransferExecutor {

	private static final Logger log = LoggerFactory.getLogger(TransferExecutor.class);

	private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

	private final AccountRepository accounts;

	private final TransferRepository transfers;

	private final LedgerEntryRepository ledger;

	private final TransferProperties limits;

	TransferExecutor(AccountRepository accounts, TransferRepository transfers, LedgerEntryRepository ledger,
			TransferProperties limits) {
		this.accounts = accounts;
		this.transfers = transfers;
		this.ledger = ledger;
		this.limits = limits;
	}

	@Transactional
	TransferResult execute(TransferCommand command, String idempotencyKey, String username) {
		String fingerprint = command.fingerprint();

		AccountRef sourceRef = accounts.findRefByAccountNumber(command.sourceAccount())
			.filter(a -> a.ownerUsername().equals(username))
			.orElseThrow(() -> new NotFoundException("Cuenta origen no encontrada"));
		AccountRef targetRef = accounts.findRefByAccountNumber(command.targetAccount())
			.orElseThrow(() -> new NotFoundException("Cuenta destino no encontrada"));
		if (sourceRef.id().equals(targetRef.id())) {
			throw new BusinessRuleException("SAME_ACCOUNT", "La cuenta origen y destino deben ser diferentes");
		}

		// Bloqueo en orden de id: si A→B y B→A llegan a la vez, ambos piden primero la cuenta de menor id,
		// así nunca se esperan mutuamente (sin deadlock).
		Account first = lock(Math.min(sourceRef.id(), targetRef.id()));
		Account second = lock(Math.max(sourceRef.id(), targetRef.id()));
		Account source = first.getId().equals(sourceRef.id()) ? first : second;
		Account target = first.getId().equals(targetRef.id()) ? first : second;

		// Con las cuentas ya bloqueadas, una petición concurrente con la misma llave ya terminó: se revisa aquí.
		Optional<TransferResult> replay = findReplay(username, idempotencyKey, fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}

		validate(command, source, target);

		source.debit(command.amount());
		target.credit(command.amount());

		Transfer transfer = transfers.save(new Transfer(idempotencyKey, username, fingerprint, source.getId(),
				target.getId(), command.amount(), command.currency(), command.description()));
		OffsetDateTime now = transfer.getCreatedAt();
		ledger.save(new LedgerEntry(transfer.getId(), source.getId(), Direction.DEBIT, command.amount(),
				source.getBalance(), now));
		ledger.save(new LedgerEntry(transfer.getId(), target.getId(), Direction.CREDIT, command.amount(),
				target.getBalance(), now));

		log.info("transferencia {} completada: {} -> {} ({} {})", transfer.getId(),
				Masking.accountNumber(command.sourceAccount()), Masking.accountNumber(command.targetAccount()),
				command.amount(), command.currency());
		return new TransferResult(transfer, false);
	}

	@Transactional(readOnly = true)
	Optional<TransferResult> findReplay(String username, String idempotencyKey, String fingerprint) {
		return transfers.findByRequestedByAndIdempotencyKey(username, idempotencyKey).map(existing -> {
			if (!existing.getRequestHash().equals(fingerprint)) {
				throw new IdempotencyConflictException();
			}
			return new TransferResult(existing, true);
		});
	}

	private Account lock(Long id) {
		return accounts.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Cuenta no encontrada"));
	}

	private void validate(TransferCommand command, Account source, Account target) {
		if (!source.isActive() || !target.isActive()) {
			throw new BusinessRuleException("ACCOUNT_INACTIVE", "Alguna de las cuentas no está activa");
		}
		if (!source.getCurrency().equals(command.currency()) || !target.getCurrency().equals(command.currency())) {
			throw new BusinessRuleException("CURRENCY_MISMATCH", "La moneda no coincide con la de las cuentas");
		}
		if (command.amount().compareTo(limits.maxAmount()) > 0) {
			throw new BusinessRuleException("AMOUNT_EXCEEDS_LIMIT", "El monto supera el límite por transferencia");
		}
		OffsetDateTime startOfDay = OffsetDateTime.now(BOGOTA).toLocalDate().atStartOfDay(BOGOTA).toOffsetDateTime();
		BigDecimal sentToday = transfers.sumOutgoingSince(source.getId(), startOfDay);
		if (sentToday.add(command.amount()).compareTo(limits.dailyLimit()) > 0) {
			throw new BusinessRuleException("DAILY_LIMIT_EXCEEDED", "La transferencia supera el límite diario");
		}
		if (source.getBalance().compareTo(command.amount()) < 0) {
			throw new BusinessRuleException("INSUFFICIENT_FUNDS", "Saldo insuficiente");
		}
	}
}
