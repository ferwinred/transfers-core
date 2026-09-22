package com.ferwin.transfers.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ferwin.transfers.common.BusinessRuleException;
import com.ferwin.transfers.common.IdempotencyConflictException;
import com.ferwin.transfers.common.InvalidRequestException;
import com.ferwin.transfers.common.NotFoundException;
import com.ferwin.transfers.support.TestData;

@SpringBootTest
@ActiveProfiles("test")
class TransferServiceTest {

	@Autowired
	TransferService service;

	@Autowired
	TestData data;

	@BeforeEach
	void setUp() {
		data.reset();
		data.account("1000000001", "ana", "COP", "500000.00");
		data.account("1000000002", "ana", "COP", "0.00");
		data.account("2000000001", "carlos", "COP", "100000.00");
		data.account("3000000001", "laura", "USD", "1000.00");
		data.blocked("9000000001", "carlos");
	}

	private TransferCommand cop(String from, String to, String amount) {
		return new TransferCommand(from, to, new BigDecimal(amount), "COP", "pago");
	}

	@Test
	void transferencia_mueve_el_saldo_y_registra_doble_partida() {
		TransferResult result = service.transfer(cop("1000000001", "2000000001", "150000.00"), "key-00000001", "ana");

		assertThat(result.replayed()).isFalse();
		assertThat(data.balance("1000000001")).isEqualByComparingTo("350000.00");
		assertThat(data.balance("2000000001")).isEqualByComparingTo("250000.00");
		assertThat(data.count("ledger_entry")).isEqualTo(2);
		assertThat(data.ledgerNetSum()).isEqualByComparingTo("0"); // débitos y créditos se cancelan
	}

	@Test
	void reintento_con_la_misma_llave_no_cobra_dos_veces() {
		TransferResult first = service.transfer(cop("1000000001", "2000000001", "100000"), "key-00000002", "ana");
		TransferResult retry = service.transfer(cop("1000000001", "2000000001", "100000.00"), "key-00000002", "ana");

		assertThat(retry.replayed()).isTrue();
		assertThat(retry.transfer().getId()).isEqualTo(first.transfer().getId());
		assertThat(data.balance("1000000001")).isEqualByComparingTo("400000.00");
		assertThat(data.count("transfer")).isEqualTo(1);
	}

	@Test
	void misma_llave_con_otra_orden_es_conflicto() {
		service.transfer(cop("1000000001", "2000000001", "100000"), "key-00000003", "ana");

		assertThatThrownBy(() -> service.transfer(cop("1000000001", "2000000001", "999"), "key-00000003", "ana"))
			.isInstanceOf(IdempotencyConflictException.class);
		assertThat(data.balance("1000000001")).isEqualByComparingTo("400000.00");
	}

	@Test
	void la_llave_es_unica_por_usuario_y_no_global() {
		service.transfer(cop("1000000001", "2000000001", "1000"), "shared-key-1", "ana");
		TransferResult carlos = service.transfer(cop("2000000001", "1000000001", "1000"), "shared-key-1", "carlos");

		assertThat(carlos.replayed()).isFalse();
		assertThat(data.count("transfer")).isEqualTo(2);
	}

	@Test
	void saldo_insuficiente_no_modifica_nada() {
		assertThatThrownBy(() -> service.transfer(cop("2000000001", "1000000001", "100000.01"), "key-00000004", "carlos"))
			.isInstanceOf(BusinessRuleException.class)
			.hasFieldOrPropertyWithValue("code", "INSUFFICIENT_FUNDS");

		assertThat(data.balance("2000000001")).isEqualByComparingTo("100000.00");
		assertThat(data.count("transfer")).isZero();
		assertThat(data.count("ledger_entry")).isZero();
	}

	@Test
	void no_se_puede_transferir_desde_una_cuenta_ajena() {
		assertThatThrownBy(() -> service.transfer(cop("2000000001", "1000000001", "1000"), "key-00000005", "ana"))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void reglas_de_negocio() {
		assertCode(() -> service.transfer(cop("1000000001", "1000000001", "1"), "key-rule-0001", "ana"), "SAME_ACCOUNT");
		assertCode(() -> service.transfer(cop("1000000001", "9000000001", "1"), "key-rule-0002", "ana"),
				"ACCOUNT_INACTIVE");
		assertCode(() -> service.transfer(cop("1000000001", "3000000001", "1"), "key-rule-0003", "ana"),
				"CURRENCY_MISMATCH");
		assertCode(() -> service.transfer(cop("1000000001", "2000000001", "10000000.01"), "key-rule-0004", "ana"),
				"AMOUNT_EXCEEDS_LIMIT");
	}

	@Test
	void limite_diario_por_cuenta_origen() {
		data.reset();
		data.account("1000000001", "ana", "COP", "30000000.00");
		data.account("2000000001", "carlos", "COP", "0.00");
		service.transfer(cop("1000000001", "2000000001", "10000000.00"), "key-daily-01", "ana");
		service.transfer(cop("1000000001", "2000000001", "10000000.00"), "key-daily-02", "ana");

		assertCode(() -> service.transfer(cop("1000000001", "2000000001", "0.01"), "key-daily-03", "ana"),
				"DAILY_LIMIT_EXCEEDED");
	}

	@Test
	void llave_de_idempotencia_obligatoria_y_con_formato_seguro() {
		assertThatThrownBy(() -> service.transfer(cop("1000000001", "2000000001", "1"), null, "ana"))
			.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.transfer(cop("1000000001", "2000000001", "1"), "x'; drop table--", "ana"))
			.isInstanceOf(InvalidRequestException.class);
	}

	private static void assertCode(Runnable call, String code) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessRuleException.class).hasFieldOrPropertyWithValue("code", code);
	}
}
