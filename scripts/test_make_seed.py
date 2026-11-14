#!/usr/bin/env python3
"""Pruebas de scripts/make-seed.py (T143): `python3 -m unittest discover -s scripts -p 'test_*.py'`.

Ejecutan el script de verdad sobre directorios temporales con un CV ficticio mínimo (nada del contenido real).
"""
import copy
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("make-seed.py")
KEY = "site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.webp"
KEY_2 = "site/projects/9f1c2d7e-2222-4a2b-8c3d-bbbbbbbbbbbb.png"


def image(**changes):
    value = {"key": KEY, "alt": "Panel de reservas", "width": 1280, "height": 720}
    value.update(changes)
    return value


def project(**changes):
    value = {
        "id": "proyecto-propio", "title": "Un proyecto", "summary": "Un resumen", "relation": "own", "disclosure": "public",
        "stack": [], "highlights": [], "links": [],
    }
    value.update(changes)
    return value


class MakeSeedTest(unittest.TestCase):
    def run_seed(self, projects):
        """Ejecuta make-seed sobre un directorio con solo projects.es.json; devuelve (código de salida, stdout, stderr)."""
        with tempfile.TemporaryDirectory() as directory:
            Path(directory, "projects.es.json").write_text(json.dumps({"projects": projects}), encoding="utf-8")
            done = subprocess.run([sys.executable, "-I", str(SCRIPT), directory], capture_output=True, text=True)
        return done.returncode, done.stdout, done.stderr

    def assertAccepted(self, projects):
        code, out, err = self.run_seed(projects)
        self.assertEqual(code, 0, err)
        return out

    def assertRejected(self, projects, fragment):
        code, out, err = self.run_seed(projects)
        self.assertNotEqual(code, 0, "debería haberse rechazado")
        self.assertIn(fragment, err)
        self.assertEqual(out, "")

    def test_valid_images_of_an_own_public_project_are_accepted_and_published(self):
        out = self.assertAccepted([project(images=[image(), image(key=KEY_2, width=800.0)])])
        self.assertIn(KEY, out)
        self.assertIn("INSERT INTO content_version", out)

    def test_absent_null_or_empty_images_are_fine(self):
        self.assertAccepted([project()])
        self.assertAccepted([project(images=[])])
        self.assertAccepted([project(images=None)])

    def test_a_bad_key_is_rejected(self):
        for key in ("site/projects/../x.png", "otra/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.png", "site/projects/x.svg",
                    "site/projects/no-uuid.png", "https://x.test/a.png", "", None, 7):
            with self.subTest(key=key):
                self.assertRejected([project(images=[image(key=key)])], "images[0].key")

    def test_images_on_employer_or_anonymous_projects_are_rejected(self):
        self.assertRejected([project(relation="employer", client="Ejemplo S.A.", images=[image()])], "solo un proyecto propio")
        self.assertRejected([project(disclosure="anonymous", images=[image()])], "solo un proyecto propio")

    def test_image_fields_are_validated(self):
        self.assertRejected([project(images=[image(alt="")])], "alt")
        self.assertRejected([project(images=[image(alt="a" * 201)])], "alt")
        for side in ("width", "height"):
            for value in (0, -1, 1.5, "10", True, 20001, None):
                with self.subTest(side=side, value=value):
                    self.assertRejected([project(images=[image(**{side: value})])], side)
        self.assertRejected([project(images=[image(caption="x")])], "campos desconocidos")
        self.assertRejected([project(images=["no es un objeto"])], "images[0]")
        self.assertRejected([project(images="x")], "images debe ser una lista")
        self.assertRejected([project(images=[image()] * 11)], "más de 10")

    def test_an_anonymous_project_with_a_client_is_still_rejected(self):
        self.assertRejected([project(disclosure="anonymous", client="Nombre Real S.A.")], "es anónimo pero trae 'client'")
        self.assertAccepted([project(disclosure="anonymous", relation="employer")])

    def test_the_sample_documents_of_the_repository_pass(self):
        sample = SCRIPT.parent.parent / "db" / "sample"
        done = subprocess.run([sys.executable, "-I", str(SCRIPT), str(sample)], capture_output=True, text=True)
        self.assertEqual(done.returncode, 0, done.stderr)
        projects = json.loads((sample / "projects.es.json").read_text(encoding="utf-8"))["projects"]
        self.assertTrue(any(p.get("images") for p in projects), "el sample debe llevar un proyecto con imágenes")
        broken = copy.deepcopy(projects)
        broken[0]["images"][0]["key"] = "mal"
        self.assertRejected(broken, "images[0].key")


if __name__ == "__main__":
    unittest.main()
