-- Instantáneas inmutables del CV, una fila por versión e idioma. La activa es la que sirve la API.
CREATE TABLE resume_version (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lang         text        NOT NULL CHECK (lang ~ '^[a-z]{2}$'),
    version      integer     NOT NULL CHECK (version > 0),
    content      jsonb       NOT NULL,
    active       boolean     NOT NULL DEFAULT false,
    published_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (lang, version)
);

-- Como mucho una versión activa por idioma.
CREATE UNIQUE INDEX resume_version_one_active_per_lang ON resume_version (lang) WHERE active;
