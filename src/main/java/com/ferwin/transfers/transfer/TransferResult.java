package com.ferwin.transfers.transfer;

/** Resultado de ejecutar una orden: la transferencia y si fue una repetición de una ya procesada. */
public record TransferResult(Transfer transfer, boolean replayed) {
}
