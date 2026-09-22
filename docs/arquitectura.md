# Arquitectura y diseño

## Componentes en AWS

```mermaid
flowchart LR
    U[Usuario] -->|HTTPS| CF[CloudFront<br/>cabeceras de seguridad]
    CF -->|/*| S3[(S3 privado<br/>Angular)]
    CF -->|/api/*| AR[App Runner<br/>Spring Boot 4 · Java 17]
    AR -->|VPC connector| RDS[(RDS PostgreSQL 16<br/>privado, cifrado)]
    AR -. lee .-> SM[Secrets Manager<br/>DB_PASSWORD · JWT_SECRET]
    ECR[(ECR<br/>scan on push)] -->|auto-deploy :latest| AR
    GH[GitHub Actions] -->|OIDC, sin llaves| ECR
    GH -->|OIDC| S3
```

## Modelo entidad-relación

```mermaid
erDiagram
    ACCOUNT ||--o{ TRANSFER : "origen"
    ACCOUNT ||--o{ TRANSFER : "destino"
    TRANSFER ||--|{ LEDGER_ENTRY : "genera 2"
    ACCOUNT ||--o{ LEDGER_ENTRY : "afecta"

    ACCOUNT {
        bigint id PK
        varchar account_number UK
        varchar owner_username
        varchar currency
        numeric balance "CHECK >= 0"
        varchar status "ACTIVE | BLOCKED"
        bigint version "bloqueo optimista"
    }
    TRANSFER {
        uuid id PK
        varchar requested_by "UK con idempotency_key"
        varchar idempotency_key
        varchar request_hash "SHA-256 de la orden"
        bigint source_account FK
        bigint target_account FK
        numeric amount "CHECK > 0"
        varchar status
    }
    LEDGER_ENTRY {
        bigint id PK
        uuid transfer_id FK
        bigint account_id FK
        varchar direction "DEBIT | CREDIT"
        numeric amount
        numeric balance_after
    }
```

## Secuencia de una transferencia

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente (Angular)
    participant API as TransferController
    participant S as TransferService
    participant X as TransferExecutor (@Transactional)
    participant DB as PostgreSQL

    C->>API: POST /api/v1/transfers + Bearer JWT + Idempotency-Key
    API->>API: Spring Security valida firma, emisor, expiración y scope transfers:write
    API->>API: Bean Validation (allow-list de formato)
    API->>S: transfer(orden, llave, usuario)
    S->>S: valida formato de la llave
    S->>X: execute()
    X->>DB: resolver id de cuentas (sin cargar entidades)
    X->>DB: SELECT ... FOR UPDATE (cuenta de menor id primero)
    X->>DB: ¿existe (usuario, llave)? → si sí, devolver la original (replay)
    X->>X: reglas: activa, moneda, límite por operación, límite diario, saldo
    X->>DB: UPDATE saldos + INSERT transfer + 2 INSERT ledger_entry
    X-->>S: COMMIT
    alt carrera perdida con la misma llave
        DB-->>S: violación de UNIQUE (la transacción se revierte completa)
        S->>DB: leer la transferencia ganadora → replay
    end
    S-->>API: resultado
    API-->>C: 201 Created (o 200 + Idempotent-Replayed: true)
```

## Decisiones técnicas (ADR resumidos)

| # | Decisión | Alternativas | Por qué |
|---|---|---|---|
| 1 | **Idempotency-Key por usuario** + hash SHA-256 de la orden | Deduplicar por contenido; sin idempotencia | Los reintentos de red son inevitables en pagos móviles. La llave distingue "reintento" de "segunda transferencia igual a propósito"; el hash detecta que se reutilizó la llave con otra orden (409). |
| 2 | **Bloqueo pesimista** (`SELECT FOR UPDATE`) en orden de id | Solo bloqueo optimista con reintentos; `UPDATE ... WHERE balance >= x` | Con alta contención sobre la misma cuenta, el optimista genera muchos reintentos. El orden fijo de bloqueo evita deadlocks en transferencias cruzadas (probado con 20 hilos). `@Version` queda como segunda defensa. |
| 3 | **Libro de doble partida** inmutable | Solo actualizar el saldo | Auditoría: el saldo se puede reconstruir y la suma de asientos de cada transferencia es 0. Es el estándar contable. |
| 4 | Resolver cuentas por **proyección** antes de bloquear | Cargar la entidad y luego bloquear | Cargar la entidad sin bloqueo deja en memoria una versión que puede envejecer; el test de concurrencia lo detectó (`StaleObjectStateException`). |
| 5 | **`BigDecimal` y `NUMERIC(19,2)`** | `double` | El punto flotante no representa exactamente valores monetarios. |
| 6 | **Restricciones en la base** (`CHECK balance >= 0`, `UNIQUE`, FK) además del código | Solo validar en Java | Defensa en profundidad: aunque haya un bug en la aplicación, la base no acepta un saldo negativo ni un duplicado. |
| 7 | **JWT validado localmente** (firma HS256, emisor, expiración, scopes) | Sesiones con cookie | API sin estado que escala horizontalmente. En un banco el emisor sería un IdP (Cognito/Entra ID/Keycloak) con RS256 y JWKS; aquí el emisor de demo solo existe en el perfil `demo`. |
| 8 | **Mismo origen** con CloudFront (`/api/*` → App Runner) | CORS abierto | Menos superficie de ataque: no hace falta permitir orígenes cruzados en producción. |
| 9 | **App Runner** | ECS Fargate + ALB; EKS; Lambda | Menos piezas que operar para una API; TLS y autoescalado incluidos. Para varios servicios o necesidades de red avanzadas pasaría a ECS/EKS. |
