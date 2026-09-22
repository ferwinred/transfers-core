package com.ferwin.transfers.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ferwin.transfers.common.BusinessRuleException;
import com.ferwin.transfers.support.TestData;

/**
 * Pruebas con hilos reales contra la base: es donde un sistema transaccional se rompe de verdad
 * (doble gasto, saldos negativos, deadlocks, doble cobro por reintentos simultáneos).
 */
@SpringBootTest
@ActiveProfiles("test")
class TransferConcurrencyTest {

	@Autowired
	TransferService service;

	@Autowired
	TestData data;

	@BeforeEach
	void setUp() {
		data.reset();
		data.account("1000000001", "ana", "COP", "1000000.00");
		data.account("2000000001", "carlos", "COP", "1000000.00");
	}

	@Test
	void veinte_retiros_simultaneos_nunca_dejan_saldo_negativo() throws Exception {
		AtomicInteger ok = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();
		runConcurrently(20, i -> () -> {
			try {
				service.transfer(new TransferCommand("1000000001", "2000000001", new BigDecimal("100000.00"), "COP", null),
						"conc-" + UUID.randomUUID(), "ana");
				ok.incrementAndGet();
			}
			catch (BusinessRuleException e) {
				rejected.incrementAndGet();
			}
			return null;
		});

		assertThat(ok.get()).isEqualTo(10); // 1.000.000 / 100.000
		assertThat(rejected.get()).isEqualTo(10);
		assertThat(data.balance("1000000001")).isEqualByComparingTo("0.00");
		assertThat(data.balance("2000000001")).isEqualByComparingTo("2000000.00");
		assertThat(data.ledgerNetSum()).isEqualByComparingTo("0");
	}

	@Test
	void transferencias_cruzadas_no_generan_deadlock() throws Exception {
		runConcurrently(20, i -> () -> {
			boolean anaToCarlos = i % 2 == 0;
			service.transfer(new TransferCommand(anaToCarlos ? "1000000001" : "2000000001",
					anaToCarlos ? "2000000001" : "1000000001", new BigDecimal("1000.00"), "COP", null),
					"cross-" + UUID.randomUUID(), anaToCarlos ? "ana" : "carlos");
			return null;
		});

		assertThat(data.count("transfer")).isEqualTo(20);
		assertThat(data.balance("1000000001")).isEqualByComparingTo("1000000.00");
		assertThat(data.balance("2000000001")).isEqualByComparingTo("1000000.00");
	}

	@Test
	void reintentos_simultaneos_con_la_misma_llave_cobran_una_sola_vez() throws Exception {
		List<Object> ids = runConcurrently(8, i -> () -> service
			.transfer(new TransferCommand("1000000001", "2000000001", new BigDecimal("50000.00"), "COP", null),
					"same-key-123", "ana")
			.transfer()
			.getId());

		Set<Object> distinct = ids.stream().collect(Collectors.toSet());
		assertThat(distinct).hasSize(1);
		assertThat(data.count("transfer")).isEqualTo(1);
		assertThat(data.balance("1000000001")).isEqualByComparingTo("950000.00");
	}

	private interface TaskFactory {

		Callable<Object> create(int index);
	}

	private static List<Object> runConcurrently(int threads, TaskFactory factory) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Object>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			Callable<Object> task = factory.create(i);
			futures.add(pool.submit(() -> {
				start.await(); // todos arrancan al mismo tiempo
				return task.call();
			}));
		}
		start.countDown();
		try {
			List<Object> results = new ArrayList<>();
			for (Future<Object> future : futures) {
				results.add(future.get(30, TimeUnit.SECONDS));
			}
			return results;
		}
		finally {
			// Si un hilo falla, se espera a los demás antes de limpiar la base para la siguiente prueba.
			pool.shutdown();
			pool.awaitTermination(30, TimeUnit.SECONDS);
		}
	}
}
