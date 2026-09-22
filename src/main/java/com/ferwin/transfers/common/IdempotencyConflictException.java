package com.ferwin.transfers.common;

/** La llave de idempotencia ya se usó con una orden diferente. Se responde con 409. */
public class IdempotencyConflictException extends RuntimeException {

	public IdempotencyConflictException() {
		super("La llave de idempotencia ya fue usada con otra orden de transferencia");
	}
}
