package com.ferwin.transfers.web;

import java.math.BigDecimal;

import com.ferwin.transfers.account.Account;

public record AccountResponse(String accountNumber, String ownerName, String currency, BigDecimal balance,
		String status) {

	public static AccountResponse from(Account account) {
		return new AccountResponse(account.getAccountNumber(), account.getOwnerName(), account.getCurrency(),
				account.getBalance(), account.getStatus().name());
	}
}
