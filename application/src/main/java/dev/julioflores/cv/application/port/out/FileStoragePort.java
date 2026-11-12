package dev.julioflores.cv.application.port.out;

import dev.julioflores.cv.application.image.StoredObject;
import java.util.List;
import java.util.Optional;

/** Almacenamiento de objetos para las imágenes (hoy Cloudflare R2). Cualquier fallo del servicio es {@code ImageStorageUnavailableException}. */
public interface FileStoragePort {

    /** Máximo de claves que admite {@link #deleteAll} en una llamada. */
    int MAX_DELETE_BATCH = 1000;

    void put(String key, byte[] content, String contentType, String cacheControl);

    /** Borra el objeto; si no existe no es un error. */
    void delete(String key);

    /** Borra hasta {@link #MAX_DELETE_BATCH} claves de una vez. */
    void deleteAll(List<String> keys);

    /** Todos los objetos cuya clave empieza por {@code prefix} (el adaptador pagina). */
    List<StoredObject> list(String prefix);

    /** La URL pública de una clave; vacío si el almacenamiento no está configurado. */
    Optional<String> publicUrl(String key);
}
