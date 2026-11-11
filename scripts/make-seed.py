#!/usr/bin/env python3
"""Genera un script SQL que publica el contenido como nueva versión activa de cada idioma.

Uso:  python3 scripts/make-seed.py DIRECTORIO [-o SALIDA.sql]
      python3 scripts/make-seed.py --rollback IDIOMA [--kind TIPO]

DIRECTORIO contiene <tipo>.<idioma>.json, por ejemplo resume.es.json, services.en.json…
Tipos: resume (el CV), services, projects y architecture (contenido editable de la web).
Por cada archivo, el SQL desactiva la versión activa de ese tipo e idioma e inserta el documento como
la siguiente versión. Sin -o escribe en la salida estándar. Solo se publican los tipos que haya en el directorio.

El contenido real vive fuera del repositorio (private/content). Para aplicarlo:
    python3 scripts/make-seed.py private/content -o private/seed.sql
    psql "$DATABASE_URL" --single-transaction -f private/seed.sql

Volver a la versión anterior de un idioma (desactiva la activa y reactiva la anterior; --kind por defecto: resume):
    python3 scripts/make-seed.py --rollback es | psql "$DATABASE_URL" --single-transaction
    python3 scripts/make-seed.py --rollback es --kind services | psql "$DATABASE_URL" --single-transaction
"""
import argparse
import json
import re
import sys
from pathlib import Path

KINDS = ("resume", "services", "projects", "architecture")
FILE_NAME = re.compile(r"^(resume|services|projects|architecture)\.([a-z]{2})\.json$")
ADAPTER_STATUS = {"in-this-api", "production", "experiment"}
RELATIONS = {"own", "employer"}
DISCLOSURES = {"public", "anonymous"}


def table_and_filter(kind: str, lang: str) -> tuple[str, str]:
    """Tabla y condición que identifican las versiones de un tipo e idioma."""
    if kind == "resume":
        return "resume_version", f"lang = '{lang}'"
    return "content_version", f"kind = '{kind}' AND lang = '{lang}'"


def statements(kind: str, lang: str, document: dict) -> str:
    tag = f"${kind}$"
    body = json.dumps(document, ensure_ascii=False, indent=2)
    if tag in body:
        sys.exit(f"{kind}.{lang}.json contiene {tag}, que se usa como delimitador del SQL")
    table, where = table_and_filter(kind, lang)
    columns, values = ("lang", f"'{lang}'") if kind == "resume" else ("kind, lang", f"'{kind}', '{lang}'")
    return (
        f"-- {kind} {lang}\n"
        f"UPDATE {table} SET active = false WHERE {where} AND active;\n"
        f"INSERT INTO {table} ({columns}, version, content, active)\n"
        f"SELECT {values}, COALESCE(MAX(version), 0) + 1, {tag}\n{body}\n{tag}::jsonb, true\n"
        f"FROM {table} WHERE {where};\n"
    )


def fail(name: str, message: str) -> None:
    sys.exit(f"{name}: {message}")


def require(name: str, item: dict, where: str, *keys: str) -> None:
    for key in keys:
        if not item.get(key):
            fail(name, f"{where} sin '{key}'")


def check_resume(name: str, lang: str, document: dict) -> None:
    missing = [key for key in ("basics", "work", "skills") if key not in document]
    if missing:
        fail(name, f"faltan {', '.join(missing)}")
    if not document["basics"].get("name"):
        fail(name, "falta basics.name")
    declared = document.get("meta", {}).get("lang")
    if declared and declared != lang:
        fail(name, f"meta.lang es '{declared}' pero el archivo es '{lang}'")


def check_services(name: str, lang: str, document: dict) -> None:
    for i, item in enumerate(document.get("services", [])):
        require(name, item, f"services[{i}]", "id", "title", "summary")
        price = item.get("price")
        if price is not None:
            if not isinstance(price.get("from"), (int, float)) or price["from"] < 0:
                fail(name, f"services[{i}].price.from debe ser un número no negativo")
            require(name, price, f"services[{i}].price", "currency")


def check_projects(name: str, lang: str, document: dict) -> None:
    for i, item in enumerate(document.get("projects", [])):
        where = f"projects[{i}]"
        require(name, item, where, "id", "title", "summary", "relation", "disclosure")
        if item["relation"] not in RELATIONS:
            fail(name, f"{where}.relation debe ser uno de {sorted(RELATIONS)}")
        if item["disclosure"] not in DISCLOSURES:
            fail(name, f"{where}.disclosure debe ser uno de {sorted(DISCLOSURES)}")
        # El nombre de un cliente anónimo no debe llegar nunca a la base de datos, ni siquiera sin exponerse.
        if item["disclosure"] == "anonymous" and item.get("client"):
            fail(name, f"{where} es anónimo pero trae 'client': quítalo y describe al cliente en 'clientLabel'")
        if item["disclosure"] == "public" and item["relation"] == "employer" and not item.get("client"):
            fail(name, f"{where} es público y de una empresa, pero no trae 'client'")


def check_architecture(name: str, lang: str, document: dict) -> None:
    require(name, document.get("core", {}), "core", "name")
    for i, port in enumerate(document.get("ports", [])):
        require(name, port, f"ports[{i}]", "id", "name")
        for j, adapter in enumerate(port.get("adapters", [])):
            require(name, adapter, f"ports[{i}].adapters[{j}]", "id", "name", "status")
            if adapter["status"] not in ADAPTER_STATUS:
                fail(name, f"ports[{i}].adapters[{j}].status debe ser uno de {sorted(ADAPTER_STATUS)}")


CHECKS = {
    "resume": check_resume,
    "services": check_services,
    "projects": check_projects,
    "architecture": check_architecture,
}


def rollback(kind: str, lang: str) -> str:
    table, where = table_and_filter(kind, lang)
    return f"""-- Vuelve a la versión anterior de '{kind}' en '{lang}'
DO $rollback$
DECLARE
    current_version int;
    previous_version int;
BEGIN
    SELECT version INTO current_version FROM {table} WHERE {where} AND active;
    SELECT MAX(version) INTO previous_version FROM {table}
        WHERE {where} AND version < COALESCE(current_version, 0);
    IF previous_version IS NULL THEN
        RAISE EXCEPTION 'No hay una versión anterior de ''{kind}'' en ''{lang}'' a la que volver';
    END IF;
    UPDATE {table} SET active = false WHERE {where} AND version = current_version;
    UPDATE {table} SET active = true WHERE {where} AND version = previous_version;
END
$rollback$;
"""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("directory", type=Path, nargs="?")
    parser.add_argument("-o", "--output", type=Path)
    parser.add_argument("--rollback", metavar="IDIOMA", help="en lugar de publicar, vuelve a la versión anterior")
    parser.add_argument("--kind", choices=KINDS, default="resume", help="con --rollback: qué contenido (por defecto resume)")
    args = parser.parse_args()

    if args.rollback:
        if not re.fullmatch(r"[a-z]{2}", args.rollback):
            sys.exit("IDIOMA debe ser un código de dos letras, por ejemplo es")
        sys.stdout.write(rollback(args.kind, args.rollback))
        return
    if not args.directory:
        parser.error("falta DIRECTORIO")

    files = sorted(p for p in args.directory.iterdir() if FILE_NAME.match(p.name))  # <tipo>.<idioma>.json
    if not files:
        sys.exit(f"No hay archivos <tipo>.<idioma>.json en {args.directory}")

    parts = ["-- Generado por scripts/make-seed.py. No editar a mano.\n"]
    for path in files:
        kind, lang = FILE_NAME.match(path.name).groups()
        document = json.loads(path.read_text(encoding="utf-8"))
        CHECKS[kind](path.name, lang, document)
        parts.append(statements(kind, lang, document))

    sql = "\n".join(parts)
    if args.output:
        args.output.write_text(sql, encoding="utf-8")
        print(f"{args.output}: {len(files)} archivo(s)", file=sys.stderr)
    else:
        sys.stdout.write(sql)


if __name__ == "__main__":
    main()
