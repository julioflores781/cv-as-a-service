-- Contenido editable de la web que no es el CV (servicios, proyectos, arquitectura): mismas reglas que resume_version.
-- Instantáneas inmutables, una fila por tipo, idioma y versión; la activa es la que sirve la API.
CREATE TABLE content_version (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind         text        NOT NULL CHECK (kind IN ('services', 'projects', 'architecture')),
    lang         text        NOT NULL CHECK (lang ~ '^[a-z]{2}$'),
    version      integer     NOT NULL CHECK (version > 0),
    content      jsonb       NOT NULL,
    active       boolean     NOT NULL DEFAULT false,
    published_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (kind, lang, version)
);

-- Como mucho una versión activa por tipo e idioma.
CREATE UNIQUE INDEX content_version_one_active ON content_version (kind, lang) WHERE active;
