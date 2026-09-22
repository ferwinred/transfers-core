-- Datos de ejemplo (ficticios) solo para el perfil demo.
INSERT INTO account (account_number, owner_name, owner_username, currency, balance, status, version, created_at) VALUES
    ('0012345678', 'Ana Gómez',    'ana',    'COP', 5000000.00, 'ACTIVE',  0, CURRENT_TIMESTAMP),
    ('0012345679', 'Ana Gómez',    'ana',    'COP',  250000.00, 'ACTIVE',  0, CURRENT_TIMESTAMP),
    ('0098765432', 'Carlos Ruiz',  'carlos', 'COP', 1200000.00, 'ACTIVE',  0, CURRENT_TIMESTAMP),
    ('0055555555', 'Cuenta Bloqueada', 'carlos', 'COP', 100000.00, 'BLOCKED', 0, CURRENT_TIMESTAMP),
    ('0077777777', 'Laura Pérez',  'laura',  'USD',    1000.00, 'ACTIVE',  0, CURRENT_TIMESTAMP);
