package dev.julioflores.cv.application.port.out;

import java.util.Set;

/** Qué imágenes usa el contenido guardado. Cuenta TODAS las versiones (activas e históricas): un rollback no debe dejar imágenes rotas. */
public interface ImageReferenceRepository {

    boolean isReferenced(String key);

    /** Todas las claves referenciadas por cualquier versión de los proyectos. */
    Set<String> referencedKeys();
}
