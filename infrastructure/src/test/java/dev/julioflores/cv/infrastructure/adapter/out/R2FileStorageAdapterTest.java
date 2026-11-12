package dev.julioflores.cv.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.image.StoredObject;
import dev.julioflores.cv.application.port.out.FileStoragePort;
import dev.julioflores.cv.application.service.ImageStorageUnavailableException;
import dev.julioflores.cv.infrastructure.MinioSupport;
import dev.julioflores.cv.infrastructure.MinioTestResource;
import dev.julioflores.cv.infrastructure.adapter.out.storage.R2FileStorageAdapter;
import dev.julioflores.cv.infrastructure.config.R2ClientConfig;
import dev.julioflores.cv.infrastructure.config.R2Settings;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/** El adaptador de R2 contra un MinIO real (T122-T124, T126). */
@QuarkusTest
@QuarkusTestResource(value = MinioTestResource.class, restrictToAnnotatedClass = true)
class R2FileStorageAdapterTest {

    private static final String KEY = "site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.webp";

    @Inject
    FileStoragePort storage;

    @BeforeEach
    void emptyBucket() {
        MinioSupport.clear();
    }

    private static HeadObjectResponse head(String key) {
        return MinioSupport.client().headObject(HeadObjectRequest.builder().bucket(MinioSupport.BUCKET).key(key).build());
    }

    private static boolean exists(String key) {
        try {
            head(key);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Test
    void theInjectedPortIsTheR2Adapter() {
        assertTrue(storage instanceof R2FileStorageAdapter || storage.getClass().getSuperclass() == R2FileStorageAdapter.class);
    }

    @Test // T122
    void anUploadedObjectExistsWithItsKeyContentTypeAndImmutableCacheAndTheUrlIsComposed() {
        byte[] content = {1, 2, 3, 4, 5};

        storage.put(KEY, content, "image/webp", "public, max-age=31536000, immutable");

        HeadObjectResponse head = head(KEY);
        assertEquals("image/webp", head.contentType());
        assertEquals("public, max-age=31536000, immutable", head.cacheControl());
        assertEquals(5, head.contentLength());
        assertEquals(Optional.of(MinioSupport.PUBLIC_URL + "/" + KEY), storage.publicUrl(KEY));
    }

    @Test // T123
    void aDeletedObjectIsGoneAndDeletingAMissingOneDoesNotFail() {
        storage.put(KEY, new byte[] {1}, "image/webp", "no-cache");
        assertTrue(exists(KEY));

        storage.delete(KEY);

        assertFalse(exists(KEY));
        assertDoesNotThrow(() -> storage.delete(KEY));
        assertDoesNotThrow(() -> storage.delete("site/projects/never-existed.png"));
    }

    @Test // T124
    void listingOnlyReturnsThePrefixWithSizeAndDateAndPagesPastAThousand() {
        List<String> inside = IntStream.range(0, 1005)
                .mapToObj(i -> String.format("site/projects/00000000-0000-4000-8000-%012d.png", i)).toList();
        inside.parallelStream().forEach(key -> MinioSupport.put(key, new byte[] {1, 2, 3}));
        MinioSupport.put("site/other/x.png", new byte[] {1});
        MinioSupport.put("elsewhere/y.png", new byte[] {1});
        Instant before = Instant.now().minus(Duration.ofMinutes(5));

        List<StoredObject> listed = storage.list("site/projects/");

        assertEquals(1005, listed.size());
        assertTrue(listed.stream().allMatch(o -> o.key().startsWith("site/projects/")));
        assertEquals(inside.stream().sorted().toList(), listed.stream().map(StoredObject::key).sorted().toList());
        assertTrue(listed.stream().allMatch(o -> o.bytes() == 3 && o.lastModified().isAfter(before)));
        assertEquals(1, storage.list("site/other/").size());
    }

    @Test
    void deleteAllRemovesUpToAThousandKeysAtOnceAndRefusesMore() {
        List<String> keys = IntStream.range(0, 1000)
                .mapToObj(i -> String.format("site/projects/00000000-0000-4000-8000-%012d.png", i)).toList();
        keys.parallelStream().forEach(key -> MinioSupport.put(key, new byte[] {1}));
        MinioSupport.put("site/projects/keep.png", new byte[] {1});

        storage.deleteAll(keys);

        assertEquals(List.of("site/projects/keep.png"), MinioSupport.keys("site/projects/"));
        storage.deleteAll(List.of());
        assertThrows(IllegalArgumentException.class, () -> storage.deleteAll(new ArrayList<>(IntStream.range(0, 1001)
                .mapToObj(i -> "k" + i).toList())));
    }

    // ── T126: servicio caído, credenciales erróneas, sin configurar ───────────────

    private record Settings(
            Optional<String> endpoint, Optional<String> accessKeyId, Optional<String> secretAccessKey, Optional<String> bucket,
            Optional<String> publicUrl, Duration connectTimeout, Duration requestTimeout) implements R2Settings {}

    private static R2FileStorageAdapter adapter(String endpoint, String user, String password, Duration connect, Duration request) {
        Settings settings = new Settings(Optional.ofNullable(endpoint), Optional.ofNullable(user), Optional.ofNullable(password),
                Optional.of(MinioSupport.BUCKET), Optional.of(MinioSupport.PUBLIC_URL), connect, request);
        return new R2FileStorageAdapter(settings, () -> R2ClientConfig.build(settings));
    }

    @Test // T126
    void aDeadServiceAnswersUnavailableWithinTheConfiguredTimeAndDoesNotHang() {
        // Puerto cerrado: rechazo inmediato. Dirección que no contesta (TEST-NET-1): solo termina por el tiempo de conexión.
        for (String endpoint : List.of("http://127.0.0.1:1", "http://192.0.2.1:9000")) {
            R2FileStorageAdapter dead = adapter(endpoint, "u", "p", Duration.ofSeconds(1), Duration.ofSeconds(2));
            long started = System.nanoTime();

            ImageStorageUnavailableException error = assertThrows(ImageStorageUnavailableException.class,
                    () -> dead.put(KEY, new byte[] {1}, "image/png", "no-cache"));

            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            assertTrue(elapsed.compareTo(Duration.ofSeconds(15)) < 0, endpoint + " tardó " + elapsed);
            assertEquals("IMAGE_STORAGE_UNAVAILABLE", error.code().name());
            assertThrows(ImageStorageUnavailableException.class, () -> dead.list("site/"));
            assertThrows(ImageStorageUnavailableException.class, () -> dead.delete(KEY));
            assertThrows(ImageStorageUnavailableException.class, () -> dead.deleteAll(List.of(KEY)));
        }
    }

    @Test // T126
    void wrongCredentialsAreUnavailableAndStoreNothing() {
        R2FileStorageAdapter wrong = adapter(MinioSupport.endpoint(), "otro", "otra-clave-incorrecta", Duration.ofSeconds(2),
                Duration.ofSeconds(5));

        assertThrows(ImageStorageUnavailableException.class, () -> wrong.put(KEY, new byte[] {1}, "image/png", "no-cache"));

        assertEquals(List.of(), MinioSupport.keys(""));
    }

    @Test
    void withoutConfigurationEveryOperationIsUnavailableButThePublicUrlStillComposes() {
        Settings none = new Settings(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of("https://media.example.test/"), Duration.ofSeconds(1), Duration.ofSeconds(1));
        R2FileStorageAdapter unconfigured = new R2FileStorageAdapter(none, () -> {
            throw new AssertionError("no debe pedir el cliente");
        });
        assertThrows(ImageStorageUnavailableException.class, () -> unconfigured.put(KEY, new byte[] {1}, "image/png", "x"));
        assertThrows(ImageStorageUnavailableException.class, () -> unconfigured.list("site/"));
        // La barra final del dominio no duplica la del medio; sin dominio, no hay URL.
        assertEquals(Optional.of("https://media.example.test/" + KEY), unconfigured.publicUrl(KEY));
        Settings noDomain = new Settings(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("  "),
                Duration.ofSeconds(1), Duration.ofSeconds(1));
        assertEquals(Optional.empty(), new R2FileStorageAdapter(noDomain, () -> null).publicUrl(KEY));
    }
}
