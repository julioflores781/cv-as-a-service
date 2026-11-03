#!/usr/bin/env python3
"""Genera un script SQL que publica el CV como nueva versión activa de cada idioma.

Uso:  python3 scripts/make-seed.py DIRECTORIO [-o SALIDA.sql]

DIRECTORIO contiene resume.<idioma>.json (por ejemplo resume.es.json y resume.en.json).
Por cada archivo, el SQL desactiva la versión activa de ese idioma e inserta el documento como
la siguiente versión. Sin -o escribe en la salida estándar.

El contenido real vive fuera del repositorio (private/content). Para aplicarlo:
    python3 scripts/make-seed.py private/content -o private/seed.sql
    psql "$DATABASE_URL" --single-transaction -f private/seed.sql
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


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("directory", type=Path)
    parser.add_argument("-o", "--output", type=Path)
    args = parser.parse_args()

    files = sorted(p for p in args.directory.iterdir() if FILE_NAME.match(p.name))
    if not files:
        sys.exit(f"No hay archivos resume.<idioma>.json en {args.directory}")

    parts = ["-- Generado por scripts/make-seed.py. No editar a mano.\n"]
    for path in files:
        document = json.loads(path.read_text(encoding="utf-8"))
        if not document.get("basics", {}).get("name"):
            sys.exit(f"{path.name}: falta basics.name")
        parts.append(statements(FILE_NAME.match(path.name).group(1), document))

    sql = "\n".join(parts)
    if args.output:
        args.output.write_text(sql, encoding="utf-8")
        print(f"{args.output}: {len(files)} idioma(s)", file=sys.stderr)
    else:
        sys.stdout.write(sql)


if __name__ == "__main__":
    main()
