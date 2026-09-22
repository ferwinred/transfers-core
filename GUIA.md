# Guía para entender y explicar este proyecto

Edwin: este proyecto está hecho a la medida de la vacante **Ingeniero/a de Software Junior · Línea Transaccionales (Bancolombia)**. Cubre Java, Angular, AWS, DevOps, backend, código seguro e IA aplicada al desarrollo. Esta guía te prepara para defenderlo en una entrevista técnica, incluso si te piden abrir el código y explicarlo en vivo.

> **Sé transparente:** es un proyecto personal con datos ficticios, construido con asistencia de IA ([docs/uso-de-ia.md](docs/uso-de-ia.md)). Eso no le resta; es justo lo que piden ("IA aplicada al desarrollo"). Lo que no puede pasar es que no sepas explicar una línea. Si una parte no la entiendes, estúdiala con esta guía antes de la entrevista.

---

## 1. La historia en 30 segundos

"Hice una API de transferencias en Java 17 con Spring Boot 4, pensando en los problemas reales de un sistema transaccional: que un reintento de red no cobre dos veces, que dos transferencias simultáneas no gasten el mismo dinero y que cada movimiento quede en un libro contable auditable. Tiene seguridad con JWT y controles del OWASP API Top 10, un front en Angular, 30 pruebas con 95 % de cobertura (incluidas pruebas de concurrencia con hilos reales), CI/CD en GitHub Actions y la infraestructura en AWS con Terraform."

## 2. Los tres problemas que resuelve (y cómo)

### 2.1 El doble cobro: idempotencia
**Problema:** el usuario pulsa "Transferir", la red se cae antes de recibir la respuesta y la app reintenta. ¿Se cobra dos veces?
**Solución:** el cliente genera una `Idempotency-Key` (un UUID) por intención de pago y la reenvía en cada reintento.
- Si la llave ya existe **para ese usuario** y la orden es la misma → se devuelve la transferencia original (`200` + `Idempotent-Replayed: true`).
- Si la llave existe pero la orden es otra (otro monto) → `409 Conflict`, porque el cliente tiene un bug o alguien está reutilizando llaves.
- ¿Cómo sé que "es la misma orden"? Guardo el **SHA-256** de los datos (`TransferCommand.fingerprint()`). La escala se normaliza: `1000` y `1000.00` son iguales.
- **La carrera:** dos reintentos llegan en el mismo milisegundo. Ninguno encuentra la llave, pero la base tiene `UNIQUE (requested_by, idempotency_key)`. Uno gana; el otro recibe una violación de unicidad, su transacción se revierte completa (sin débito) y `TransferService` devuelve la transferencia ganadora. La prueba `reintentos_simultaneos_con_la_misma_llave_cobran_una_sola_vez` lanza 8 hilos con la misma llave y verifica que se cobró una sola vez.

### 2.2 El doble gasto: concurrencia
**Problema:** Ana tiene $1.000.000 y llegan 20 retiros de $100.000 al mismo tiempo. Sin control, varios leen "saldo = 1.000.000" y todos pasan.
**Solución:** `SELECT ... FOR UPDATE` (bloqueo pesimista, `AccountRepository.findByIdForUpdate`). La primera transacción bloquea la fila y las demás esperan su turno.
- **Deadlock:** si A→B bloquea A y luego B, mientras B→A bloquea B y luego A, se esperan para siempre. Lo evito **bloqueando siempre primero la cuenta de menor id**, así todas piden los candados en el mismo orden. La prueba `transferencias_cruzadas_no_generan_deadlock` lo verifica con 20 hilos.
- **Timeout de 3 s:** si una cuenta está muy ocupada, el cliente recibe `503` y reintenta (con la misma llave, así que no hay riesgo).
- **Defensa en profundidad:** `@Version` (bloqueo optimista) y `CHECK (balance >= 0)` en la base. Aunque hubiera un bug en Java, PostgreSQL no guardaría un saldo negativo.
- **El bug que encontraron las pruebas:** al principio cargaba la cuenta por número (sin bloqueo) y luego la bloqueaba. Si otra transacción la modificaba entre medio, Hibernate tenía dos versiones de la misma entidad (`StaleObjectStateException`). Lo arreglé resolviendo solo el id con una proyección (`AccountRef`) y cargando la entidad **ya bloqueada**. Esta historia es oro para una entrevista: demuestra que las pruebas de concurrencia sirven.

### 2.3 La auditoría: libro de doble partida
Cada transferencia crea **dos asientos** en `ledger_entry`: un DEBIT en el origen y un CREDIT en el destino, con el saldo resultante. Los asientos nunca se editan. La suma de todos los asientos es 0 (las pruebas lo verifican) y el saldo de cualquier cuenta se puede reconstruir sumando sus asientos. Es el principio contable que usa cualquier banco.

## 3. Recorrido por el código (en orden de lectura)

1. `db/migration/V1__schema.sql`: el modelo. Fíjate en los `CHECK`, el `UNIQUE` compuesto y los índices por `(account_id, created_at)` para consultar movimientos rápido.
2. `account/Account.java`: `BigDecimal` para el dinero (nunca `double`), `@Version` y `debit()`, que no permite quedar en negativo.
3. `transfer/TransferService.java`: valida la llave y maneja la carrera de idempotencia.
4. `transfer/TransferExecutor.java`: **el corazón**. `@Transactional` (todo o nada), bloqueo ordenado, verificación de replay, reglas de negocio (cuenta activa, moneda, límite por operación y diario, saldo) y asientos contables.
5. `web/TransferController.java`: `@PreAuthorize("hasAuthority('SCOPE_transfers:write')")`, `201` vs `200` en replay y cuenta destino enmascarada.
6. `web/ApiExceptionHandler.java`: cada excepción se traduce a un código HTTP con cuerpo RFC 9457, sin trazas.
7. `security/SecurityConfig.java`: dos cadenas de filtros (docs y API), JWT stateless, CSP estricta y CORS con lista explícita.
8. `web/CorrelationIdFilter.java`: trazabilidad de punta a punta en los logs.
9. `web/src/app/`: `api.ts` (servicio + interceptor que agrega el token) y `app.ts` (signals y formulario reactivo; la llave de idempotencia **solo cambia tras un éxito**).

## 4. Preguntas probables y respuestas

**¿Por qué pesimista y no optimista?**
Con mucha contención sobre la misma cuenta (una empresa que recibe miles de pagos), el optimista hace fallar y reintentar muchas transacciones. El pesimista las pone en fila. Para cuentas con poca contención el optimista sería más eficiente.

**¿Qué nivel de aislamiento usas?**
El por defecto de PostgreSQL: READ COMMITTED. Con `FOR UPDATE` basta para este caso. SERIALIZABLE también funcionaría, pero con más abortos que habría que reintentar.

**¿Por qué `BigDecimal`?**
`0.1 + 0.2` en `double` no da exactamente `0.3`. En dinero, ese error acumulado es inaceptable.

**¿Qué es `@Transactional` y qué pasa si falla a la mitad?**
Abre una transacción y hace commit al final, o rollback si hay una excepción no controlada. Si falla después del débito y antes del crédito, se revierte todo: el dinero nunca desaparece.

**¿Cómo evitas que un usuario vea las cuentas de otro?**
Comparo el dueño de la cuenta con el `sub` del token (BOLA, el riesgo #1 del OWASP API Top 10). Y respondo 404 aunque la cuenta exista, para que no se pueda averiguar qué números de cuenta son válidos.

**¿Por qué JWT y no sesión?**
La API no guarda estado, así que escala horizontalmente (App Runner puede levantar más instancias sin compartir sesiones). En un banco real el token lo emitiría un proveedor de identidad (Cognito, Entra ID) con RS256. El emisor de demo solo existe en el perfil `demo` y el bean ni se crea en producción.

**¿Dónde guardas el token en el front?**
En memoria (una signal), no en `localStorage`. Si alguien lograra ejecutar JavaScript en la página (XSS), no encontraría un token persistido que robar.

**¿Cómo se despliega?**
GitHub Actions construye la imagen Docker, la sube a ECR y App Runner la despliega automáticamente. El front va a S3 y CloudFront lo sirve; CloudFront también enruta `/api/*` a App Runner (mismo origen, sin CORS). GitHub se autentica con **OIDC**: no hay llaves de AWS guardadas en ningún lado. Todo está en Terraform.

**¿Qué pruebas tiene?**
30: unitarias (enmascaramiento, huella, secreto débil), integración del servicio (reglas de negocio, idempotencia), concurrencia con hilos reales (doble gasto, deadlock, reintentos simultáneos) y API/seguridad con MockMvc (401, 403, 404 por BOLA, 409, 422, validación, cabeceras, token alterado). Cobertura del 95 %, y el build falla si baja del 80 %.

**¿Qué mejorarías?**
Un proveedor de identidad real, rate limiting (WAF), Testcontainers con PostgreSQL en las pruebas en vez de H2, el patrón outbox para publicar eventos de transferencia (Kafka/SNS) y trazas distribuidas con OpenTelemetry.

**¿Cómo usaste la IA?**
La usé para acelerar el borrador del código, las pruebas y la infraestructura. Lo que hice yo: decidir la arquitectura, revisar todo, correr las pruebas y corregir lo que fallaba. Por ejemplo, la prueba de concurrencia encontró un bug en el primer borrador. Tengo reglas claras: no pasarle secretos ni código de la empresa, verificar contra la documentación oficial y no dejar nada que no pueda explicar. Así también, en mi trabajo, aumenté cerca de un 40 % mi productividad.

## 5. Conexión con la vacante

| La vacante pide | Dónde lo muestras |
|---|---|
| Java | Todo el backend (Java 17, records, Spring Boot 4) |
| Angular | `web/` (Angular 21, signals, formularios reactivos, interceptor) |
| AWS | App Runner, RDS, Secrets Manager, ECR, S3, CloudFront, IAM/OIDC |
| DevOps | Docker, docker-compose, CI, CodeQL, Dependabot, CD con OIDC, Terraform |
| Desarrollo backend | Diseño transaccional, JPA, Flyway, API REST versionada |
| Buenas prácticas | Pruebas, cobertura mínima, errores estándar, capas claras, ADRs |
| Código seguro | `docs/seguridad.md`: cada control con su prueba |
| IA aplicada al desarrollo | `docs/uso-de-ia.md` |
| Documentación (ER, APIs, componentes cloud) | `docs/arquitectura.md` (ER, secuencia, componentes) + OpenAPI |
| Ciclo de vida completo | Del diseño al despliegue, con soporte (correlation id, health checks) |

## 6. Practica antes de la entrevista

1. `docker compose up --build`, entra como **ana** y transfiere a `0098765432`. Luego intenta pasar el saldo o transferir a la cuenta bloqueada `0055555555`.
2. En Swagger (`./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`), repite una transferencia con la misma llave y mira el header `Idempotent-Replayed`.
3. Comenta el orden de bloqueo en `TransferExecutor` y corre `TransferConcurrencyTest`: ¿falla? Explica por qué.
4. Dibuja de memoria el diagrama de secuencia de `docs/arquitectura.md`.
5. Explícale a alguien no técnico qué es la idempotencia usando el ejemplo del botón "Pagar" pulsado dos veces.
