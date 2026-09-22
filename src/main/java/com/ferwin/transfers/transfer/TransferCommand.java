package com.ferwin.transfers.transfer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Orden de transferencia ya validada en su forma (la validación de negocio la hace el servicio). */
public record TransferCommand(String sourceAccount, String targetAccount, BigDecimal amount, String currency,
		String description) {

	/** Huella del contenido de la orden. La escala se normaliza: 1000 y 1000.00 son la misma orden. */
	public String fingerprint() {
		String canonical = String.join("|", sourceAccount, targetAccount, amount.setScale(2).toPlainString(), currency,
				description == null ? "" : description);
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 no disponible", e);
		}
	}
}
