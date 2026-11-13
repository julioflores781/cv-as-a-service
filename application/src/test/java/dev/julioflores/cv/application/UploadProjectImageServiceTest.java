package dev.julioflores.cv.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.AdminFakes.RecordingAuditLog;
import dev.julioflores.cv.application.audit.AuditAction;
import dev.julioflores.cv.application.error.ErrorCode;
import dev.julioflores.cv.application.image.UploadedImage;
import dev.julioflores.cv.application.service.ImageStorageUnavailableException;
import dev.julioflores.cv.application.service.UploadProjectImageService;
import dev.julioflores.cv.application.error.ApplicationException;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class UploadProjectImageServiceTest {

    private static final Pattern KEY = Pattern.compile(
            "site/projects/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)");

    private final ImageFakes.Storage storage = new ImageFakes.Storage();
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private final UploadProjectImageService service = new UploadProjectImageService(storage, audit);

    private ErrorCode codeOf(byte[] content) {
        return assertThrows(ApplicationException.class, () -> service.upload(content)).code();
    }

    @Test // T50
    void aValidJpegIsStoredUnderAGeneratedKeyWithTheDetectedTypeAndImmutableCache() {
        byte[] jpeg = ImageSamples.jpeg(1280, 720, 100 * 1024);

        UploadedImage uploaded = service.upload(jpeg);

        assertEquals(1, storage.puts.size());
        ImageFakes.Put put = storage.puts.get(0);
        assertTrue(KEY.matcher(put.key()).matches() && put.key().endsWith(".jpg"), put.key());
        assertEquals("image/jpeg", put.contentType());
        assertEquals("public, max-age=31536000, immutable", put.cacheControl());
        assertArrayEquals(jpeg, put.content());
        assertEquals(put.key(), uploaded.key());
        assertEquals("https://media.example.test/" + put.key(), uploaded.url());
        assertEquals("image/jpeg", uploaded.contentType());
        assertEquals(100 * 1024, uploaded.bytes());
        assertEquals(1280, uploaded.width());
        assertEquals(720, uploaded.height());
    }

    @Test // T51
    void fiveMegabytesPlusOneByteIsTooLargeAndNeverReachesTheStorage() {
        assertEquals(ErrorCode.IMAGE_TOO_LARGE, codeOf(ImageSamples.jpeg(10, 10, 5 * 1024 * 1024 + 1)));
        assertEquals(0, storage.calls);
        // El límite es inclusivo: exactamente 5 MB se acepta.
        service.upload(ImageSamples.jpeg(10, 10, 5 * 1024 * 1024));
        assertEquals(1, storage.puts.size());
    }

    @Test // T52
    void emptyOrNonImageContentIsInvalid() {
        assertEquals(ErrorCode.IMAGE_INVALID, codeOf(new byte[0]));
        assertEquals(ErrorCode.IMAGE_INVALID, codeOf(null));
        assertEquals(ErrorCode.IMAGE_INVALID, codeOf("<html><body>hola</body></html>".getBytes()));
        assertEquals(ErrorCode.IMAGE_INVALID, codeOf(new byte[64]));
        // Cabecera de PNG truncada antes de las dimensiones: no se puede describir, así que no se acepta.
        byte[] truncated = java.util.Arrays.copyOf(ImageSamples.png(5, 5), 20);
        assertEquals(ErrorCode.IMAGE_INVALID, codeOf(truncated));
        assertEquals(0, storage.calls);
    }

    @Test // T53
    void anSvgIsAnUnsupportedType() {
        assertEquals(ErrorCode.IMAGE_UNSUPPORTED_TYPE, codeOf(ImageSamples.svg()));
        assertEquals(0, storage.calls);
    }

    @Test // T54
    void theClientsNameAndDeclaredTypeAreNotEvenAnInputSoTheKeyIsOnlyServerGenerated() {
        // El caso de uso solo recibe los bytes: un «../../x.php» o un «text/html» no tienen por dónde entrar.
        UploadedImage uploaded = service.upload(ImageSamples.png(30, 20));
        assertTrue(KEY.matcher(uploaded.key()).matches() && uploaded.key().endsWith(".png"), uploaded.key());
        assertEquals("image/png", uploaded.contentType());
        UploadedImage again = service.upload(ImageSamples.png(30, 20));
        assertTrue(!again.key().equals(uploaded.key()), "cada subida genera una clave nueva");
    }

    @Test // T55
    void aFailingStorageIsImageStorageUnavailable() {
        storage.failure = new IllegalStateException("boom");
        ImageStorageUnavailableException error =
                assertThrows(ImageStorageUnavailableException.class, () -> service.upload(ImageSamples.png(5, 5)));
        assertEquals(ErrorCode.IMAGE_STORAGE_UNAVAILABLE, error.code());

        storage.failure = new ImageStorageUnavailableException("not configured");
        assertEquals(ErrorCode.IMAGE_STORAGE_UNAVAILABLE, codeOf(ImageSamples.png(5, 5)));
    }

    @Test
    void aValidUploadRecordsKeyTypeBytesAndDimensions() { // T40
        UploadedImage uploaded = service.upload(ImageSamples.jpeg(1280, 720, 100 * 1024));
        assertEquals(RecordingAuditLog.success(AuditAction.IMAGE_UPLOADED, Map.of("key", uploaded.key(),
                "content_type", "image/jpeg", "bytes", "102400", "width", "1280", "height", "720")), audit.only());
    }

    @Test
    void invalidTooLargeUnsupportedOrFailingStorageUploadsRecordNothing() { // T40 (negativos)
        codeOf(new byte[0]);
        codeOf(ImageSamples.jpeg(10, 10, 5 * 1024 * 1024 + 1));
        codeOf(ImageSamples.svg());
        storage.failure = new IllegalStateException("boom");
        assertThrows(ImageStorageUnavailableException.class, () -> service.upload(ImageSamples.png(5, 5)));
        storage.failure = null;
        assertEquals(java.util.List.of(), audit.events);
    }
}
