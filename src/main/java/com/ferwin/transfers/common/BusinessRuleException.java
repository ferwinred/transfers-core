package com.ferwin.transfers.common;

/** Regla de negocio incumplida (saldo insuficiente, cuenta bloqueada...). Se responde con 422. */
public class BusinessRuleException extends RuntimeException {

	private final String code;

	public BusinessRuleException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
