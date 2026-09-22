package com.ferwin.transfers.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de seguridad. Falla al arrancar si el secreto no existe o es débil:
 * es preferible no levantar el servicio a levantarlo con un secreto adivinable.
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(String jwtSecret, String issuer, List<String> allowedOrigins,
		boolean demoTokenEndpoint) {

	public SecurityProperties {
		if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("app.security.jwt-secret (JWT_SECRET) es obligatorio y debe tener al menos 32 bytes");
		}
		allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins;
	}
}
