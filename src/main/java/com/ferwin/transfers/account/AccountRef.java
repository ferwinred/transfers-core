package com.ferwin.transfers.account;

/**
 * Solo el id y el dueño de una cuenta. Se usa para resolver el número de cuenta sin cargar la entidad:
 * si se cargara antes de bloquearla, la versión en memoria podría quedar vieja frente a otra transacción
 * concurrente (lo detectó TransferConcurrencyTest).
 */
public record AccountRef(Long id, String ownerUsername) {
}
