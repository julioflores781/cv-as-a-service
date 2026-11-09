-- Mensajes dejados desde el formulario de contacto. La IP nunca se guarda en claro: solo un hash con sal.
CREATE TABLE contact_message (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       text        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 100),
    email      text        NOT NULL CHECK (char_length(email) BETWEEN 3 AND 254),
    body       text        NOT NULL CHECK (char_length(body) BETWEEN 10 AND 2000),
    ip_hash    text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX contact_message_created_at ON contact_message (created_at DESC);
