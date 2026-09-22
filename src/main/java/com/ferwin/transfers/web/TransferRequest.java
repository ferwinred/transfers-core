package com.ferwin.transfers.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.ferwin.transfers.transfer.TransferCommand;

/** Validación de forma en el borde (allow-list): lo que no cumple el patrón ni siquiera llega al servicio. */
public record TransferRequest(
		@NotBlank @Pattern(regexp = "\\d{10}", message = "debe tener 10 dígitos") String sourceAccount,
		@NotBlank @Pattern(regexp = "\\d{10}", message = "debe tener 10 dígitos") String targetAccount,
		@NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
		@NotBlank @Pattern(regexp = "[A-Z]{3}", message = "debe ser un código ISO 4217") String currency,
		@Size(max = 140) @Pattern(regexp = "[\\p{L}\\p{N} .,#-]*", message = "contiene caracteres no permitidos")
		String description) {

	public TransferCommand toCommand() {
		return new TransferCommand(sourceAccount, targetAccount, amount, currency, description);
	}
}
