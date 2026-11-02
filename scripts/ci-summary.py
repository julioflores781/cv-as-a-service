#!/usr/bin/env python3
"""Resumen del pipeline en Markdown: resultado de cada prueba y cobertura de JaCoCo.

Lee los informes que deja `mvn verify` y escribe Markdown por la salida estándar; en GitHub Actions se
redirige a $GITHUB_STEP_SUMMARY. Solo usa la biblioteca estándar.

Sale con código 1 si hay pruebas que fallan, si faltan informes o si la cobertura queda por debajo de los umbrales.

    python3 scripts/ci-summary.py [--root .] [--min-total 90] [--min-module 80]
"""
import argparse
import glob
import os
import sys
import xml.etree.ElementTree as ET
from collections import OrderedDict

STATUS_ICON = {"passed": "✅", "failed": "❌", "error": "💥", "skipped": "⏭️"}


def pct(covered, missed):
    total = covered + missed
    return 100.0 * covered / total if total else None


def fmt(value):
    return "—" if value is None else f"{value:.1f}%"


def light(value):
    if value is None:
        return "⚪"
    return "🟢" if value >= 90 else "🟡" if value >= 75 else "🔴"


def seconds(value):
    return f"{value * 1000:.0f} ms" if value < 1 else f"{value:.2f} s"


def module_of(path, root):
    return os.path.relpath(path, root).split(os.sep)[0]


# ── Pruebas (Surefire) ─────────────────────────────────────────────────────────

def read_tests(root):
    suites = []
    for path in sorted(glob.glob(os.path.join(root, "*", "target", "surefire-reports", "TEST-*.xml"))):
        suite = ET.parse(path).getroot()
        cases = []
        for case in suite.findall("testcase"):
            problem = case.find("failure")
            status, message = "passed", ""
            if problem is not None:
                status = "failed"
            elif case.find("error") is not None:
                problem, status = case.find("error"), "error"
            elif case.find("skipped") is not None:
                status = "skipped"
            if problem is not None:
                message = (problem.get("message") or problem.text or "").strip()
            cases.append((case.get("name"), status, float(case.get("time") or 0), message))
        suites.append({
            "module": module_of(path, root),
            "class": suite.get("name"),
            "time": float(suite.get("time") or 0),
            "cases": cases,
        })
    return suites


def count(suite, status):
    return sum(1 for _, s, _, _ in suite["cases"] if s == status)


# ── Cobertura (JaCoCo) ─────────────────────────────────────────────────────────

def counters(node):
    return {c.get("type"): (int(c.get("covered")), int(c.get("missed"))) for c in node.findall("counter")}


def read_coverage(root):
    patterns = ("*/target/site/jacoco/jacoco.xml", "*/target/jacoco-report/jacoco.xml")
    modules = OrderedDict()
    for pattern in patterns:
        for path in sorted(glob.glob(os.path.join(root, pattern))):
            xml = ET.parse(path).getroot()
            packages, classes = [], []
            for package in xml.findall("package"):
                name = package.get("name").replace("/", ".")
                packages.append((name, counters(package)))
                for cls in package.findall("class"):
                    classes.append((cls.get("name").replace("/", "."), counters(cls)))
            modules[module_of(path, root)] = {"total": counters(xml), "packages": packages, "classes": classes}
    return modules


def total_of(modules, kind):
    covered = sum(m["total"].get(kind, (0, 0))[0] for m in modules.values())
    missed = sum(m["total"].get(kind, (0, 0))[1] for m in modules.values())
    return covered, missed


# ── Salida ─────────────────────────────────────────────────────────────────────

def render(suites, modules, min_total, min_module):
    out, ok = [], True
    cases = [c for s in suites for c in s["cases"]]
    n = len(cases)
    failed = sum(1 for c in cases if c[1] in ("failed", "error"))
    skipped = sum(1 for c in cases if c[1] == "skipped")
    passed = n - failed - skipped
    tests_ok = n > 0 and failed == 0

    line_c, line_m = total_of(modules, "LINE")
    total_line = pct(line_c, line_m)
    gate_total = total_line is not None and total_line >= min_total
    module_fail = [name for name, m in modules.items()
                   if (pct(*m["total"].get("LINE", (0, 0))) or 0) < min_module]
    coverage_ok = bool(modules) and gate_total and not module_fail
    ok = tests_ok and coverage_ok

    out.append(f"# {'✅' if ok else '❌'} Pruebas y cobertura\n")
    out.append(f"**{passed} de {n} pruebas pasan**"
               + (f" · {failed} fallan" if failed else "")
               + (f" · {skipped} omitidas" if skipped else "")
               + f" · {seconds(sum(s['time'] for s in suites))} · cobertura de líneas **{fmt(total_line)}**\n")
    if n == 0:
        out.append("> ❌ No se encontró ningún informe de Surefire: el build falló antes de ejecutar las pruebas.\n")

    # Cobertura por módulo
    out.append("## Cobertura por módulo\n")
    out.append("| Módulo | Líneas | Ramas | Métodos | Instrucciones |")
    out.append("|---|---:|---:|---:|---:|")
    for name, m in modules.items():
        t = m["total"]
        line = pct(*t.get("LINE", (0, 0)))
        out.append(f"| {light(line)} `{name}` | **{fmt(line)}** ({t['LINE'][0]}/{sum(t['LINE'])}) "
                   f"| {fmt(pct(*t.get('BRANCH', (0, 0))))} | {fmt(pct(*t.get('METHOD', (0, 0))))} "
                   f"| {fmt(pct(*t.get('INSTRUCTION', (0, 0))))} |")
    if modules:
        out.append(f"| **Total** | **{fmt(total_line)}** ({line_c}/{line_c + line_m}) "
                   f"| {fmt(pct(*total_of(modules, 'BRANCH')))} | {fmt(pct(*total_of(modules, 'METHOD')))} "
                   f"| {fmt(pct(*total_of(modules, 'INSTRUCTION')))} |")
    out.append("")
    out.append(f"- {'✅' if gate_total else '❌'} Umbral de cobertura total de líneas: {fmt(total_line)} (mínimo {min_total}%)")
    out.append(f"- {'✅' if not module_fail else '❌'} Umbral por módulo: mínimo {min_module}%"
               + (f" — por debajo: {', '.join(f'`{x}`' for x in module_fail)}" if module_fail else ""))
    if not modules:
        out.append("- ❌ No se encontró ningún informe de JaCoCo")
    out.append("\n_Cada módulo se mide con sus propias pruebas; `infrastructure` solo cuenta sus clases._\n")

    # Pruebas por clase
    out.append("## Pruebas por clase\n")
    out.append("| Módulo | Clase de pruebas | Pruebas | ✅ | ❌ | ⏭️ | Tiempo |")
    out.append("|---|---|---:|---:|---:|---:|---:|")
    for s in suites:
        bad = count(s, "failed") + count(s, "error")
        out.append(f"| `{s['module']}` | `{s['class'].split('.')[-1]}` | {len(s['cases'])} | {count(s, 'passed')} "
                   f"| {bad or ''} | {count(s, 'skipped') or ''} | {seconds(s['time'])} |")
    out.append("")

    # Fallos arriba del todo si los hay
    if failed:
        out.append("## ❌ Fallos\n")
        for s in suites:
            for name, status, _, message in s["cases"]:
                if status in ("failed", "error"):
                    out.append(f"**`{s['class'].split('.')[-1]}.{name}`**\n\n```\n{message[:1500]}\n```\n")

    # Detalle de cada prueba
    out.append("## Detalle de cada prueba\n")
    for s in suites:
        icon = "✅" if count(s, "failed") + count(s, "error") == 0 else "❌"
        out.append(f"<details><summary>{icon} <code>{s['module']}</code> · "
                   f"<b>{s['class'].split('.')[-1]}</b> ({len(s['cases'])})</summary>\n")
        for name, status, took, _ in s["cases"]:
            out.append(f"- {STATUS_ICON[status]} `{name}` — {seconds(took)}")
        out.append("\n</details>\n")

    # Cobertura por paquete y clases con líneas sin cubrir
    out.append("## Cobertura por paquete\n")
    out.append("<details><summary>Ver paquetes</summary>\n")
    out.append("| Módulo | Paquete | Líneas | Ramas |")
    out.append("|---|---|---:|---:|")
    for name, m in modules.items():
        for package, c in m["packages"]:
            line = pct(*c.get("LINE", (0, 0)))
            out.append(f"| `{name}` | `{package}` | {light(line)} {fmt(line)} | {fmt(pct(*c.get('BRANCH', (0, 0))))} |")
    out.append("\n</details>\n")

    uncovered = []
    for name, m in modules.items():
        for cls, c in m["classes"]:
            covered, missed = c.get("LINE", (0, 0))
            if missed:
                uncovered.append((missed, name, cls.split(".")[-1], covered))
    out.append("## Clases con líneas sin cubrir\n")
    if uncovered:
        out.append("<details><summary>Ver las 15 con más líneas sin cubrir</summary>\n")
        out.append("| Módulo | Clase | Sin cubrir | Cobertura |")
        out.append("|---|---|---:|---:|")
        for missed, name, cls, covered in sorted(uncovered, reverse=True)[:15]:
            out.append(f"| `{name}` | `{cls}` | {missed} | {fmt(pct(covered, missed))} |")
        out.append("\n</details>\n")
    else:
        out.append("Todas las clases tienen todas sus líneas cubiertas. 🎉\n")

    return "\n".join(out), ok


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", default=".", help="raíz del proyecto Maven")
    parser.add_argument("--min-total", type=float, default=90.0, help="cobertura mínima total de líneas (%%)")
    parser.add_argument("--min-module", type=float, default=80.0, help="cobertura mínima de líneas por módulo (%%)")
    args = parser.parse_args()

    markdown, ok = render(read_tests(args.root), read_coverage(args.root), args.min_total, args.min_module)
    print(markdown)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
