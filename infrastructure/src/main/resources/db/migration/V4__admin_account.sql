-- Un único administrador. La fila solo existe cuando se crea la primera contraseña con el comando del NAS: no hay credencial sembrada.
CREATE TABLE admin_account (
    id                  smallint    PRIMARY KEY CHECK (id = 1),       -- imposible tener dos administradores
    password_hash       text        NOT NULL CHECK (password_hash LIKE '$argon2id$%'),
    session_version     integer     NOT NULL DEFAULT 1 CHECK (session_version > 0),
    failed_attempts     integer     NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    locked_until        timestamptz,
    password_changed_at timestamptz NOT NULL DEFAULT now(),
    last_login_at       timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now()
);
