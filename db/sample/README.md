# CV ficticio de ejemplo

Datos inventados («Ada Ejemplo») con la misma estructura que el CV real. Los usan las pruebas y sirven de seed para
arrancar la API sin datos privados.

- `resume.es.json` y `resume.en.json` son la fuente; `V1000__sample_resume.sql` se **genera** con
  `python3 scripts/make-seed.py db/sample -o db/sample/V1000__sample_resume.sql` (vuelve a ejecutarlo si editas los JSON).
- Maven copia el `.sql` al classpath de las pruebas como migración Flyway `V1000` (solo en test).
- El CV real no está en el repositorio: vive en `private/` (ignorado por git).
