-- Esquema transaccional. Compatible con PostgreSQL (producción) y H2 en modo PostgreSQL (demo y pruebas).

CREATE TABLE account (
    id              BIGSERIAL PRIMARY KEY,
    account_number  VARCHAR(20)    NOT NULL UNIQUE,
    owner_name      VARCHAR(120)   NOT NULL,
    owner_username  VARCHAR(60)    NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    balance         NUMERIC(19, 2) NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_account_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'BLOCKED'))
);

CREATE INDEX ix_account_owner ON account (owner_username);

CREATE TABLE transfer (
    id               UUID PRIMARY KEY,
    idempotency_key  VARCHAR(64)    NOT NULL,
    requested_by     VARCHAR(60)    NOT NULL,
    request_hash     VARCHAR(64)    NOT NULL,
    source_account   BIGINT         NOT NULL REFERENCES account (id),
    target_account   BIGINT         NOT NULL REFERENCES account (id),
    amount           NUMERIC(19, 2) NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    description      VARCHAR(140),
    status           VARCHAR(20)    NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    -- La llave de idempotencia es única por usuario: dos clientes distintos pueden generar la misma.
    CONSTRAINT uq_transfer_idempotency UNIQUE (requested_by, idempotency_key),
    CONSTRAINT ck_transfer_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transfer_distinct_accounts CHECK (source_account <> target_account)
);

CREATE INDEX ix_transfer_source_created ON transfer (source_account, created_at);

-- Libro contable de doble partida: cada transferencia genera exactamente un débito y un crédito.
CREATE TABLE ledger_entry (
    id             BIGSERIAL PRIMARY KEY,
    transfer_id    UUID           NOT NULL REFERENCES transfer (id),
    account_id     BIGINT         NOT NULL REFERENCES account (id),
    direction      VARCHAR(6)     NOT NULL,
    amount         NUMERIC(19, 2) NOT NULL,
    balance_after  NUMERIC(19, 2) NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_ledger_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_amount_positive CHECK (amount > 0)
);

CREATE INDEX ix_ledger_account_created ON ledger_entry (account_id, created_at);
