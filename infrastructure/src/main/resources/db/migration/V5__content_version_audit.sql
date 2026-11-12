-- Quién y por qué publicó cada versión del contenido editable. Las filas existentes son del script (make-seed.py).
-- Solo añade columnas con valor por defecto: no toca los datos ni la restricción de una sola versión activa.
ALTER TABLE content_version
    ADD COLUMN comment text CHECK (comment IS NULL OR char_length(comment) <= 200),
    ADD COLUMN source  text NOT NULL DEFAULT 'seed' CHECK (source IN ('seed', 'admin'));
