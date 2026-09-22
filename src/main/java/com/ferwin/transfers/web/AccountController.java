package com.ferwin.transfers.web;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ferwin.transfers.account.Account;
import com.ferwin.transfers.account.AccountRepository;
import com.ferwin.transfers.common.NotFoundException;
import com.ferwin.transfers.ledger.LedgerEntryRepository;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Cuentas")
@SecurityRequirement(name = "bearer")
@PreAuthorize("hasAuthority('SCOPE_accounts:read')")
@Transactional(readOnly = true)
public class AccountController {

	private final AccountRepository accounts;

	private final LedgerEntryRepository ledger;

	public AccountController(AccountRepository accounts, LedgerEntryRepository ledger) {
		this.accounts = accounts;
		this.ledger = ledger;
	}

	@GetMapping
	@Operation(summary = "Cuentas del usuario autenticado")
	public List<AccountResponse> mine(@AuthenticationPrincipal Jwt jwt) {
		return accounts.findByOwnerUsernameOrderByAccountNumber(jwt.getSubject())
			.stream()
			.map(AccountResponse::from)
			.toList();
	}

	@GetMapping("/{accountNumber}/movements")
	@Operation(summary = "Movimientos de una cuenta propia (más recientes primero)")
	public List<MovementResponse> movements(@PathVariable String accountNumber,
			@RequestParam(defaultValue = "20") int limit, @AuthenticationPrincipal Jwt jwt) {
		Account account = accounts.findByAccountNumber(accountNumber)
			.filter(a -> a.getOwnerUsername().equals(jwt.getSubject()))
			.orElseThrow(() -> new NotFoundException("Cuenta no encontrada"));
		int safeLimit = Math.max(1, Math.min(limit, 100));
		return ledger.findByAccountIdOrderByCreatedAtDescIdDesc(account.getId(), PageRequest.of(0, safeLimit))
			.stream()
			.map(MovementResponse::from)
			.toList();
	}
}
