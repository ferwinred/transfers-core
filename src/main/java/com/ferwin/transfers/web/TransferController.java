package com.ferwin.transfers.web;

import java.net.URI;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ferwin.transfers.account.AccountRepository;
import com.ferwin.transfers.transfer.TransferResult;
import com.ferwin.transfers.transfer.TransferService;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transferencias")
@SecurityRequirement(name = "bearer")
public class TransferController {

	private final TransferService service;

	private final AccountRepository accounts;

	public TransferController(TransferService service, AccountRepository accounts) {
		this.service = service;
		this.accounts = accounts;
	}

	@PostMapping
	@PreAuthorize("hasAuthority('SCOPE_transfers:write')")
	@Operation(summary = "Crea una transferencia entre cuentas",
			description = "Idempotente: repetir la petición con el mismo Idempotency-Key devuelve la transferencia original (200) sin volver a debitar.")
	public ResponseEntity<TransferResponse> create(
			@Parameter(description = "Llave única por intención de pago (por ejemplo un UUID)", required = true)
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody TransferRequest request, @AuthenticationPrincipal Jwt jwt) {
		TransferResult result = service.transfer(request.toCommand(), idempotencyKey, jwt.getSubject());
		String source = accounts.findById(result.transfer().getSourceAccountId()).orElseThrow().getAccountNumber();
		String target = accounts.findById(result.transfer().getTargetAccountId()).orElseThrow().getAccountNumber();
		TransferResponse body = TransferResponse.from(result.transfer(), source, target);
		if (result.replayed()) {
			return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(body);
		}
		return ResponseEntity.created(URI.create("/api/v1/transfers/" + body.id())).body(body);
	}
}
