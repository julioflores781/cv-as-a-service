package dev.julioflores.cv.application;

import dev.julioflores.cv.application.image.StoredObject;
import dev.julioflores.cv.application.port.out.FileStoragePort;
import dev.julioflores.cv.application.port.out.ImageReferenceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Dobles de prueba de los puertos de imágenes. */
final class ImageFakes {

    private ImageFakes() {}

    record Put(String key, byte[] content, String contentType, String cacheControl) {}

    static final class Storage implements FileStoragePort {

        final Map<String, StoredObject> objects = new LinkedHashMap<>();
        final List<Put> puts = new ArrayList<>();
        final List<String> deleted = new ArrayList<>();
        final List<Integer> batchSizes = new ArrayList<>();
        RuntimeException failure;
        int calls;

        void add(String key, Instant lastModified) {
            objects.put(key, new StoredObject(key, 100, lastModified));
        }

        @Override
        public void put(String key, byte[] content, String contentType, String cacheControl) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            puts.add(new Put(key, content, contentType, cacheControl));
            objects.put(key, new StoredObject(key, content.length, Instant.EPOCH));
        }

        @Override
        public void delete(String key) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            deleted.add(key);
            objects.remove(key);
        }

        @Override
        public void deleteAll(List<String> keys) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            batchSizes.add(keys.size());
            deleted.addAll(keys);
            keys.forEach(objects::remove);
        }

        @Override
        public List<StoredObject> list(String prefix) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return objects.values().stream().filter(o -> o.key().startsWith(prefix)).toList();
        }

        @Override
        public Optional<String> publicUrl(String key) {
            return Optional.of("https://media.example.test/" + key);
        }
    }

    static final class References implements ImageReferenceRepository {

        final Set<String> keys = new java.util.HashSet<>();

        @Override
        public boolean isReferenced(String key) {
            return keys.contains(key);
        }

        @Override
        public Set<String> referencedKeys() {
            return Set.copyOf(keys);
        }
    }
}
