package com.ferwin.transfers.common;

/** Petición mal formada (por ejemplo, sin Idempotency-Key). Se responde con 400. */
public class InvalidRequestException extends RuntimeException {

	public InvalidRequestException(String message) {
		super(message);
	}
}
