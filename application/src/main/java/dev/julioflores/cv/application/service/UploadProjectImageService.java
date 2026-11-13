package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.audit.AuditAction;
import dev.julioflores.cv.application.audit.AuditEvent;
import dev.julioflores.cv.application.image.UploadedImage;
import dev.julioflores.cv.application.port.in.UploadProjectImageUseCase;
import dev.julioflores.cv.application.port.out.AuditLog;
import dev.julioflores.cv.application.port.out.FileStoragePort;
import dev.julioflores.cv.domain.ImageDimensions;
import dev.julioflores.cv.domain.ImageFormat;
import dev.julioflores.cv.domain.ImageKey;
import java.util.UUID;

public class UploadProjectImageService implements UploadProjectImageUseCase {

    /** 5 MB. */
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    /** La clave es inmutable (UUID nuevo en cada subida), así que el CDN y el navegador pueden guardarla para siempre. */
    public static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final FileStoragePort storage;
    private final AuditLog audit;

    public UploadProjectImageService(FileStoragePort storage, AuditLog audit) {
        this.storage = storage;
        this.audit = audit;
    }

    @Override
    public UploadedImage upload(byte[] content) {
        if (content == null || content.length == 0) {
            throw new ImageInvalidException("the file is missing or empty");
        }
        if (content.length > MAX_BYTES) {
            throw new ImageTooLargeException(MAX_BYTES);
        }
        ImageFormat format = ImageFormat.detect(content).orElseThrow(() -> ImageFormat.isKnownUnsupported(content)
                ? new ImageUnsupportedTypeException()
                : new ImageInvalidException("the bytes are not a JPEG, PNG, WebP or GIF image"));
        ImageDimensions size = ImageDimensions.read(format, content)
                .orElseThrow(() -> new ImageInvalidException("the " + format + " header is truncated or corrupt"));
        // Nada del cliente (nombre, tipo declarado) llega hasta aquí: la clave y el tipo salen de los bytes.
        String key = ImageKey.of(UUID.randomUUID(), format);
        String url;
        try {
            storage.put(key, content, format.mediaType(), CACHE_CONTROL);
            url = storage.publicUrl(key).orElseThrow(() -> new ImageStorageUnavailableException("public URL is not configured"));
        } catch (ImageStorageUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ImageStorageUnavailableException(e.getClass().getSimpleName(), e);
        }
        audit.record(AuditEvent.success(AuditAction.IMAGE_UPLOADED, "key", key, "content_type", format.mediaType(),
                "bytes", content.length, "width", size.width(), "height", size.height()));
        return new UploadedImage(key, url, format.mediaType(), content.length, size.width(), size.height());
    }
}
