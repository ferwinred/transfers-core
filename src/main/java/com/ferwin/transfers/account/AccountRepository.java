package com.ferwin.transfers.account;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

	Optional<Account> findByAccountNumber(String accountNumber);

	List<Account> findByOwnerUsernameOrderByAccountNumber(String ownerUsername);

	@Query("select new com.ferwin.transfers.account.AccountRef(a.id, a.ownerUsername) from Account a where a.accountNumber = :number")
	Optional<AccountRef> findRefByAccountNumber(@Param("number") String accountNumber);

	/**
	 * SELECT ... FOR UPDATE: bloquea la fila hasta que termine la transacción.
	 * El timeout evita que una transacción quede esperando indefinidamente.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
	@Query("select a from Account a where a.id = :id")
	Optional<Account> findByIdForUpdate(@Param("id") Long id);
}
