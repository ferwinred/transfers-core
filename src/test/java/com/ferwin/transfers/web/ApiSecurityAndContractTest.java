package com.ferwin.transfers.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.ferwin.transfers.support.TestData;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityAndContractTest {

	private static final String BODY = """
			{"sourceAccount":"1000000001","targetAccount":"2000000001","amount":150000.00,"currency":"COP","description":"arriendo"}
			""";

	@Autowired
	WebApplicationContext context;

	@Autowired
	CorrelationIdFilter correlationIdFilter;

	@Autowired
	TestData data;

	MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(correlationIdFilter).apply(springSecurity()).build();
		data.reset();
		data.account("1000000001", "ana", "COP", "500000.00");
		data.account("2000000001", "carlos", "COP", "0.00");
	}

	private static JwtRequestPostProcessor user(String name, String... scopes) {
		SimpleGrantedAuthority[] authorities = new SimpleGrantedAuthority[scopes.length];
		for (int i = 0; i < scopes.length; i++) {
			authorities[i] = new SimpleGrantedAuthority("SCOPE_" + scopes[i]);
		}
		return jwt().jwt(j -> j.subject(name)).authorities(authorities);
	}

	@Test
	void sin_token_responde_401() throws Exception {
		mvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void sin_el_permiso_de_escritura_responde_403() throws Exception {
		mvc.perform(post("/api/v1/transfers").with(user("ana", "accounts:read"))
			.header("Idempotency-Key", "api-key-0001")
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY)).andExpect(status().isForbidden());
	}

	@Test
	void crea_la_transferencia_y_un_reintento_devuelve_la_original() throws Exception {
		mvc.perform(post("/api/v1/transfers").with(user("ana", "transfers:write"))
			.header("Idempotency-Key", "api-key-0002")
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY))
			.andExpect(status().isCreated())
			.andExpect(header().exists("Location"))
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.targetAccount").value("******0001")); // la cuenta ajena va enmascarada

		mvc.perform(post("/api/v1/transfers").with(user("ana", "transfers:write"))
			.header("Idempotency-Key", "api-key-0002")
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY)).andExpect(status().isOk()).andExpect(header().string("Idempotent-Replayed", "true"));
	}

	@Test
	void errores_en_formato_problem_json_sin_detalles_internos() throws Exception {
		mvc.perform(post("/api/v1/transfers").with(user("ana", "transfers:write"))
			.header("Idempotency-Key", "api-key-0003")
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY.replace("150000.00", "900000.00")))
			.andExpect(status().isUnprocessableContent())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
			.andExpect(content().string(not(containsString("Exception"))))
			.andExpect(content().string(not(containsString("ledger_entry"))));
	}

	@Test
	void validacion_de_entrada_rechaza_datos_mal_formados() throws Exception {
		mvc.perform(post("/api/v1/transfers").with(user("ana", "transfers:write"))
			.header("Idempotency-Key", "api-key-0004")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"sourceAccount":"1' OR '1'='1","targetAccount":"2000000001","amount":-5,"currency":"cop","description":"<script>"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.sourceAccount").exists())
			.andExpect(jsonPath("$.errors.amount").exists())
			.andExpect(jsonPath("$.errors.currency").exists())
			.andExpect(jsonPath("$.errors.description").exists());
	}

	@Test
	void sin_idempotency_key_responde_400() throws Exception {
		mvc.perform(post("/api/v1/transfers").with(user("ana", "transfers:write"))
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY)).andExpect(status().isBadRequest());
	}

	@Test
	void json_invalido_responde_400() throws Exception {
		mvc.perform(post("/api/v1/transfers").with(user("ana", "transfers:write"))
			.header("Idempotency-Key", "api-key-0005")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{no es json")).andExpect(status().isBadRequest());
	}

	@Test
	void misma_llave_con_otro_cuerpo_responde_409() throws Exception {
		mvc.perform(post("/api/v1/transfers").with(user("ana", "transfers:write"))
			.header("Idempotency-Key", "api-key-0006")
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY)).andExpect(status().isCreated());
		mvc.perform(post("/api/v1/transfers").with(user("ana", "transfers:write"))
			.header("Idempotency-Key", "api-key-0006")
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY.replace("150000.00", "1.00"))).andExpect(status().isConflict());
	}

	@Test
	void cada_usuario_solo_ve_sus_cuentas_y_movimientos() throws Exception {
		mvc.perform(get("/api/v1/accounts").with(user("carlos", "accounts:read")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].accountNumber").value("2000000001"));

		mvc.perform(get("/api/v1/accounts/1000000001/movements").with(user("carlos", "accounts:read")))
			.andExpect(status().isNotFound()); // existe, pero es de ana: mismo 404 que si no existiera
	}

	@Test
	void movimientos_de_la_cuenta_propia() throws Exception {
		mvc.perform(post("/api/v1/transfers").with(user("ana", "transfers:write"))
			.header("Idempotency-Key", "api-key-0007")
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY)).andExpect(status().isCreated());

		mvc.perform(get("/api/v1/accounts/1000000001/movements").with(user("ana", "accounts:read")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].direction").value("DEBIT"))
			.andExpect(jsonPath("$[0].balanceAfter").value(350000.00));
	}

	@Test
	void correlation_id_se_propaga_y_se_sanea() throws Exception {
		mvc.perform(get("/actuator/health").header("X-Correlation-Id", "abc-12345678"))
			.andExpect(status().isOk())
			.andExpect(header().string("X-Correlation-Id", "abc-12345678"));
		mvc.perform(get("/actuator/health").header("X-Correlation-Id", "malo\r\nInjected: header"))
			.andExpect(header().string("X-Correlation-Id", not(containsString("Injected"))));
	}

	@Test
	void cabeceras_de_seguridad() throws Exception {
		mvc.perform(get("/api/v1/accounts").with(user("ana", "accounts:read")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
			.andExpect(header().string("Referrer-Policy", "no-referrer"));
	}

	@Test
	void token_de_demo_funciona_de_punta_a_punta() throws Exception {
		String response = mvc.perform(post("/api/v1/auth/demo-token").contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"ana\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String token = response.replaceAll(".*\"access_token\":\"([^\"]+)\".*", "$1");

		mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)));
		mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + token + "x"))
			.andExpect(status().isUnauthorized()); // firma alterada
		mvc.perform(post("/api/v1/auth/demo-token").contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"mallory\"}")).andExpect(status().isNotFound());
	}

	@Test
	void documentacion_openapi_publica() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/v1/transfers']").exists());
	}
}
