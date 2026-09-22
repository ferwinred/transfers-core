package com.ferwin.transfers.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ferwin.transfers.account.Account;
import com.ferwin.transfers.security.SecurityProperties;
import com.ferwin.transfers.transfer.TransferCommand;

class UnitTest {

	@Test
	void enmascara_numeros_de_cuenta() {
		assertThat(Masking.accountNumber("0012345678")).isEqualTo("******5678");
		assertThat(Masking.accountNumber("123")).isEqualTo("****");
		assertThat(Masking.accountNumber(null)).isEqualTo("****");
	}

	@Test
	void la_huella_ignora_la_escala_del_monto_pero_no_el_contenido() {
		TransferCommand a = new TransferCommand("1", "2", new BigDecimal("1000"), "COP", null);
		TransferCommand b = new TransferCommand("1", "2", new BigDecimal("1000.00"), "COP", "");
		TransferCommand c = new TransferCommand("1", "2", new BigDecimal("1000.01"), "COP", null);
		assertThat(a.fingerprint()).isEqualTo(b.fingerprint()).hasSize(64);
		assertThat(a.fingerprint()).isNotEqualTo(c.fingerprint());
	}

	@Test
	void no_arranca_con_un_secreto_debil() {
		assertThatThrownBy(() -> new SecurityProperties("corto", "iss", List.of(), false))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new SecurityProperties(null, "iss", null, false))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void una_cuenta_no_queda_en_negativo() {
		Account account = new Account("1", "Ana", "ana", "COP", new BigDecimal("10.00"));
		assertThatThrownBy(() -> account.debit(new BigDecimal("10.01"))).isInstanceOf(IllegalStateException.class);
		account.debit(new BigDecimal("10.00"));
		assertThat(account.getBalance()).isEqualByComparingTo("0");
	}
}
