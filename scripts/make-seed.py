#!/usr/bin/env python3
"""Genera un script SQL que publica el CV como nueva versión activa de cada idioma.

Uso:  python3 scripts/make-seed.py DIRECTORIO [-o SALIDA.sql]
      python3 scripts/make-seed.py --rollback IDIOMA

DIRECTORIO contiene resume.<idioma>.json (por ejemplo resume.es.json y resume.en.json).
Por cada archivo, el SQL desactiva la versión activa de ese idioma e inserta el documento como
la siguiente versión. Sin -o escribe en la salida estándar.

El contenido real vive fuera del repositorio (private/content). Para aplicarlo:
    python3 scripts/make-seed.py private/content -o private/seed.sql
    psql "$DATABASE_URL" --single-transaction -f private/seed.sql

Volver a la versión anterior de un idioma (desactiva la activa y reactiva la anterior):
    python3 scripts/make-seed.py --rollback es | psql "$DATABASE_URL" --single-transaction
"""
import argparse
import json
import re
import sys
from pathlib import Path

TAG = "$resume$"
FILE_NAME = re.compile(r"^resume\.([a-z]{2})\.json$")


def statements(lang: str, document: dict) -> str:
    body = json.dumps(document, ensure_ascii=False, indent=2)
    if TAG in body:
        sys.exit(f"resume.{lang}.json contiene {TAG}, que se usa como delimitador del SQL")
    return (
        f"-- {lang}\n"
        f"UPDATE resume_version SET active = false WHERE lang = '{lang}' AND active;\n"
        f"INSERT INTO resume_version (lang, version, content, active)\n"
        f"SELECT '{lang}', COALESCE(MAX(version), 0) + 1, {TAG}\n{body}\n{TAG}::jsonb, true\n"
        f"FROM resume_version WHERE lang = '{lang}';\n"
    )


REQUIRED = ("basics", "work", "skills")


def check(name: str, lang: str, document: dict) -> None:
    """Rechaza lo que dejaría la API rota o sirviendo el idioma equivocado."""
    missing = [key for key in REQUIRED if key not in document]
    if missing:
        sys.exit(f"{name}: faltan {', '.join(missing)}")
    if not document["basics"].get("name"):
        sys.exit(f"{name}: falta basics.name")
    declared = document.get("meta", {}).get("lang")
    if declared and declared != lang:
        sys.exit(f"{name}: meta.lang es '{declared}' pero el archivo es '{lang}'")


def rollback(lang: str) -> str:
    return f"""-- Vuelve a la versión anterior de '{lang}'
DO $rollback$
DECLARE
    current_version int;
    previous_version int;
BEGIN
    SELECT version INTO current_version FROM resume_version WHERE lang = '{lang}' AND active;
    SELECT MAX(version) INTO previous_version FROM resume_version
        WHERE lang = '{lang}' AND version < COALESCE(current_version, 0);
    IF previous_version IS NULL THEN
        RAISE EXCEPTION 'No hay una versión anterior de ''{lang}'' a la que volver';
    END IF;
    UPDATE resume_version SET active = false WHERE lang = '{lang}' AND version = current_version;
    UPDATE resume_version SET active = true WHERE lang = '{lang}' AND version = previous_version;
END
$rollback$;
"""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("directory", type=Path, nargs="?")
    parser.add_argument("-o", "--output", type=Path)
    parser.add_argument("--rollback", metavar="IDIOMA", help="en lugar de publicar, vuelve a la versión anterior")
    args = parser.parse_args()

    if args.rollback:
        if not re.fullmatch(r"[a-z]{2}", args.rollback):
            sys.exit("IDIOMA debe ser un código de dos letras, por ejemplo es")
        sys.stdout.write(rollback(args.rollback))
        return
    if not args.directory:
        parser.error("falta DIRECTORIO")

    files = sorted(p for p in args.directory.iterdir() if FILE_NAME.match(p.name))  # resume.<idioma>.json
    if not files:
        sys.exit(f"No hay archivos resume.<idioma>.json en {args.directory}")

    parts = ["-- Generado por scripts/make-seed.py. No editar a mano.\n"]
    for path in files:
        document = json.loads(path.read_text(encoding="utf-8"))
        lang = FILE_NAME.match(path.name).group(1)
        check(path.name, lang, document)
        parts.append(statements(lang, document))

    sql = "\n".join(parts)
    if args.output:
        args.output.write_text(sql, encoding="utf-8")
        print(f"{args.output}: {len(files)} idioma(s)", file=sys.stderr)
    else:
        sys.stdout.write(sql)


if __name__ == "__main__":
    main()
