package com.ferwin.transfers.transfer;

import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.ferwin.transfers.common.InvalidRequestException;

/**
 * Punto de entrada de las transferencias.
 *
 * Idempotencia: el cliente envía una llave única por intención de pago (header Idempotency-Key).
 * Si la red falla y el cliente reintenta con la misma llave, se devuelve la transferencia original
 * en lugar de cobrar dos veces.
 */
@Service
public class TransferService {

	private static final Pattern KEY_FORMAT = Pattern.compile("[A-Za-z0-9-]{8,64}");

	private final TransferExecutor executor;

	public TransferService(TransferExecutor executor) {
		this.executor = executor;
	}

	public TransferResult transfer(TransferCommand command, String idempotencyKey, String username) {
		if (idempotencyKey == null || !KEY_FORMAT.matcher(idempotencyKey).matches()) {
			throw new InvalidRequestException(
					"El header Idempotency-Key es obligatorio (8 a 64 caracteres alfanuméricos o guiones)");
		}
		try {
			return executor.execute(command, idempotencyKey, username);
		}
		catch (DataIntegrityViolationException raceLost) {
			// Dos peticiones con la misma llave llegaron a la vez: la otra ganó la restricción única.
			// Esta transacción ya se revirtió completa, así que no hubo doble débito.
			return executor.findReplay(username, idempotencyKey, command.fingerprint()).orElseThrow(() -> raceLost);
		}
	}
}
