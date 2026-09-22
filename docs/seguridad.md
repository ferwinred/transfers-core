# Código seguro: qué se hizo y dónde (referencia OWASP Top 10 y OWASP API Security Top 10)

| Riesgo | Control | Dónde |
|---|---|---|
| **Autorización rota a nivel de objeto (API1 / BOLA)** | La cuenta origen debe pertenecer al `sub` del token; los movimientos solo se muestran al dueño. Cuenta ajena e inexistente responden igual (404) para no permitir enumeración | `TransferExecutor`, `AccountController`, test `cada_usuario_solo_ve_sus_cuentas_y_movimientos` |
| **Autenticación rota (API2)** | JWT con firma HS256, emisor y expiración validados; token de 15 min; el servicio no arranca con un secreto de menos de 32 bytes | `SecurityConfig`, `SecurityProperties`, test `no_arranca_con_un_secreto_debil` |
| **Autorización por función (API5)** | Scopes: `accounts:read` para consultar, `transfers:write` para mover dinero (`@PreAuthorize`) | Controladores, test `sin_el_permiso_de_escritura_responde_403` |
| **Inyección (A03)** | JPA con parámetros enlazados (nunca concatenación de SQL); validación de entrada por allow-list (regex de cuenta, moneda ISO, descripción) | `TransferRequest`, test `validacion_de_entrada_rechaza_datos_mal_formados` |
| **Consumo de recursos (API4)** | Límite por transferencia y límite diario; `limit` de movimientos acotado a 100; timeout de bloqueo de 3 s | `application.yml`, `AccountController`, `AccountRepository` |
| **Flujos de negocio sensibles (API6)** | Idempotencia: un reintento no cobra dos veces; conflicto 409 si se reutiliza la llave con otra orden | `TransferService`, pruebas de concurrencia |
| **Exposición de información (A01/A05)** | Errores RFC 9457 sin trazas ni nombres de tablas (`include-stacktrace: never`); cuenta destino enmascarada; números de cuenta enmascarados en logs; Swagger apagado en producción | `ApiExceptionHandler`, `Masking`, `application-prod.yml` |
| **Configuración de seguridad (A05)** | Cabeceras: CSP `default-src 'none'`, `nosniff`, `no-referrer`, sin frames; CORS con lista explícita; sin consola H2 | `SecurityConfig`, test `cabeceras_de_seguridad` |
| **Inyección en logs** | El `X-Correlation-Id` recibido se valida con regex; si no cumple se reemplaza | `CorrelationIdFilter`, test `correlation_id_se_propaga_y_se_sanea` |
| **Secretos** | Cero secretos versionados, ni siquiera de ejemplo: el perfil `demo` genera un JWT aleatorio en cada arranque (`${random.value}`), `docker compose` exige `DB_PASSWORD` desde un `.env` ignorado por git, y en AWS los valores vienen de Secrets Manager. GitHub Actions usa OIDC, sin llaves de acceso. Un escaneo de secretos (GitGuardian) sobre el primer commit encontró los valores de ejemplo y se eliminaron | `application-demo.yml`, `docker-compose.yml`, `.env.example`, `infra/terraform/secrets.tf`, `deploy.yml` |
| **Componentes vulnerables (A06)** | Dependabot (Maven, npm, Actions, Docker), `npm audit` en CI, escaneo de imágenes en ECR, CodeQL (SAST) semanal | `.github/` |
| **Integridad de datos** | `CHECK balance >= 0`, `UNIQUE (requested_by, idempotency_key)`, llaves foráneas: la base rechaza estados inválidos aunque haya un bug | `V1__schema.sql` |
| **Front** | Token solo en memoria (no `localStorage`); Angular escapa la interpolación por defecto (sin `innerHTML`); CSP en Nginx/CloudFront | `web/src/app/api.ts`, `web/nginx.conf` |
| **Contenedor** | Proceso como usuario sin privilegios; imagen JRE mínima | `Dockerfile` |

## Pendiente para un entorno real

- Emisor de identidad real (Cognito/Entra ID) con RS256 + JWKS y rotación de llaves.
- Rate limiting por usuario (API Gateway o WAF delante de CloudFront).
- Cifrado de campos sensibles y auditoría de accesos (CloudTrail + logs inmutables).
- Pruebas DAST (OWASP ZAP) en el pipeline.
