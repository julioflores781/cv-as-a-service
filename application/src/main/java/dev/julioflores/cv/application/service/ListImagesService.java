package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.image.ImageEntry;
import dev.julioflores.cv.application.image.StoredObject;
import dev.julioflores.cv.application.port.in.ListImagesUseCase;
import dev.julioflores.cv.application.port.out.FileStoragePort;
import dev.julioflores.cv.application.port.out.ImageReferenceRepository;
import dev.julioflores.cv.domain.ImageKey;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class ListImagesService implements ListImagesUseCase {

    private final FileStoragePort storage;
    private final ImageReferenceRepository references;

    public ListImagesService(FileStoragePort storage, ImageReferenceRepository references) {
        this.storage = storage;
        this.references = references;
    }

    @Override
    public List<ImageEntry> list() {
        List<StoredObject> objects = Storage.call(() -> storage.list(ImageKey.PREFIX));
        Set<String> referenced = references.referencedKeys();
        return objects.stream()
                .filter(object -> ImageKey.isValid(object.key()))
                .sorted(Comparator.comparing(StoredObject::lastModified).reversed().thenComparing(StoredObject::key))
                .map(object -> new ImageEntry(
                        object.key(),
                        storage.publicUrl(object.key()).orElse(object.key()),
                        object.bytes(),
                        object.lastModified(),
                        referenced.contains(object.key())))
                .toList();
    }
}
