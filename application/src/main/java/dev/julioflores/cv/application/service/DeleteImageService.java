package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.image.StoredObject;
import dev.julioflores.cv.application.port.in.DeleteImageUseCase;
import dev.julioflores.cv.application.port.out.FileStoragePort;
import dev.julioflores.cv.application.port.out.ImageReferenceRepository;
import dev.julioflores.cv.domain.ImageKey;
import java.util.List;

public class DeleteImageService implements DeleteImageUseCase {

    private final FileStoragePort storage;
    private final ImageReferenceRepository references;

    public DeleteImageService(FileStoragePort storage, ImageReferenceRepository references) {
        this.storage = storage;
        this.references = references;
    }

    @Override
    public void delete(String id) {
        if (!ImageKey.isUuid(id)) {
            throw new ImageNotFoundException(String.valueOf(id));
        }
        // La extensión no viaja en el id: se busca la clave por su UUID (el prefijo incluye el punto, así no hay falsas coincidencias).
        List<StoredObject> found = Storage.call(() -> storage.list(ImageKey.PREFIX + id + "."));
        String key = found.stream().map(StoredObject::key).filter(ImageKey::isValid).findFirst()
                .orElseThrow(() -> new ImageNotFoundException(id));
        if (references.isReferenced(key)) {
            throw new ImageInUseException(key);
        }
        Storage.run(() -> storage.delete(key));
    }
}
