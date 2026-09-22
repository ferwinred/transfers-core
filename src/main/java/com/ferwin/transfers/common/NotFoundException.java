package com.ferwin.transfers.common;

/**
 * Recurso inexistente o ajeno. Ambos casos responden igual (404) para no revelar
 * si una cuenta de otro usuario existe (evita enumeración de cuentas).
 */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}
