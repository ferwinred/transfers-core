package com.ferwin.transfers.common;

/** Enmascaramiento de datos sensibles para logs y respuestas a terceros. */
public final class Masking {

	private Masking() {
	}

	/** "0012345678" → "******5678". */
	public static String accountNumber(String value) {
		if (value == null || value.length() <= 4) {
			return "****";
		}
		return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
	}
}
