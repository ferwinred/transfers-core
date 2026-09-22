package com.ferwin.transfers.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

import javax.crypto.SecretKey;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ferwin.transfers.common.NotFoundException;

/**
 * Emisor de tokens SOLO para la demo local. En un banco los tokens los emite un proveedor de identidad
 * (Cognito, Entra ID, Keycloak...) y esta API solo los valida. El bean ni siquiera existe fuera del perfil demo.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Demo")
@ConditionalOnProperty(name = "app.security.demo-token-endpoint", havingValue = "true")
public class DemoTokenController {

	private static final Set<String> DEMO_USERS = Set.of("ana", "carlos", "laura");

	private final NimbusJwtEncoder encoder;

	private final SecurityProperties props;

	public DemoTokenController(SecretKey jwtKey, SecurityProperties props) {
		this.encoder = NimbusJwtEncoder.withSecretKey(jwtKey).build();
		this.props = props;
	}

	public record DemoTokenRequest(@NotBlank String username) {
	}

	@PostMapping("/demo-token")
	@Operation(summary = "Token de prueba (ana, carlos o laura). Solo existe en el perfil demo.")
	public Map<String, Object> token(@Valid @RequestBody DemoTokenRequest request) {
		if (!DEMO_USERS.contains(request.username())) {
			throw new NotFoundException("Usuario de demo no encontrado");
		}
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(props.issuer())
			.subject(request.username())
			.issuedAt(now)
			.expiresAt(now.plus(15, ChronoUnit.MINUTES))
			.claim("scope", "accounts:read transfers:write")
			.build();
		String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
		return Map.of("access_token", token, "token_type", "Bearer", "expires_in", 900);
	}
}
