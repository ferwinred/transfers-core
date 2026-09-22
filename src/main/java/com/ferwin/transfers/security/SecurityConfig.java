package com.ferwin.transfers.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	/** Documentación (Swagger UI) con las cabeceras por defecto: la CSP estricta de la API la rompería. */
	@Bean
	@Order(1)
	SecurityFilterChain docs(HttpSecurity http) throws Exception {
		http.securityMatcher("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		return http.build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain api(HttpSecurity http, SecurityProperties props) throws Exception {
		http.csrf(csrf -> csrf.disable()) // API sin cookies: el token viaja en el header Authorization
			.cors(Customizer.withDefaults())
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> {
				auth.requestMatchers("/actuator/health/**").permitAll();
				if (props.demoTokenEndpoint()) {
					auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/demo-token").permitAll();
				}
				auth.anyRequest().authenticated();
			})
			.oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
			.headers(h -> h.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
				.referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER)));
		return http.build();
	}

	@Bean
	SecretKey jwtKey(SecurityProperties props) {
		return new SecretKeySpec(props.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	/** Valida firma, expiración y emisor. Un token de otro emisor se rechaza aunque la firma sea válida. */
	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtKey, SecurityProperties props) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(props.issuer())));
		return decoder;
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(SecurityProperties props) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(props.allowedOrigins()); // lista explícita, nunca "*"
		cors.addAllowedMethod(HttpMethod.GET);
		cors.addAllowedMethod(HttpMethod.POST);
		cors.addAllowedHeader("Authorization");
		cors.addAllowedHeader("Content-Type");
		cors.addAllowedHeader("Idempotency-Key");
		cors.addExposedHeader("Idempotent-Replayed");
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", cors);
		return source;
	}
}
