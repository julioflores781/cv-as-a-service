package dev.julioflores.cv.application.port.in;

public interface DeleteImageUseCase {

    /** Borra la imagen cuyo UUID es {@code id}, salvo que alguna versión del contenido la referencie. */
    void delete(String id);
}
