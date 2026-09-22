package com.ferwin.transfers.support;

import java.math.BigDecimal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.ferwin.transfers.account.Account;
import com.ferwin.transfers.account.AccountRepository;

/** Deja la base limpia y crea cuentas de prueba. */
@Component
public class TestData {

	private final JdbcTemplate jdbc;

	private final AccountRepository accounts;

	public TestData(JdbcTemplate jdbc, AccountRepository accounts) {
		this.jdbc = jdbc;
		this.accounts = accounts;
	}

	public void reset() {
		jdbc.update("delete from ledger_entry");
		jdbc.update("delete from transfer");
		jdbc.update("delete from account");
	}

	public Account account(String number, String owner, String currency, String balance) {
		return accounts.save(new Account(number, owner.toUpperCase(), owner, currency, new BigDecimal(balance)));
	}

	public Account blocked(String number, String owner) {
		Account account = new Account(number, owner, owner, "COP", new BigDecimal("1000000.00"));
		account.block();
		return accounts.save(account);
	}

	public BigDecimal balance(String number) {
		return accounts.findByAccountNumber(number).orElseThrow().getBalance();
	}

	public int count(String table) {
		return jdbc.queryForObject("select count(*) from " + table, Integer.class);
	}

	public BigDecimal ledgerNetSum() {
		return jdbc.queryForObject(
				"select coalesce(sum(case when direction = 'CREDIT' then amount else -amount end), 0) from ledger_entry",
				BigDecimal.class);
	}
}
