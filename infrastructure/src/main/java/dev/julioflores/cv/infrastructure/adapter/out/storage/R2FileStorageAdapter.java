package dev.julioflores.cv.infrastructure.adapter.out.storage;

import dev.julioflores.cv.application.image.StoredObject;
import dev.julioflores.cv.application.port.out.FileStoragePort;
import dev.julioflores.cv.application.service.ImageStorageUnavailableException;
import dev.julioflores.cv.infrastructure.config.R2Settings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Las imágenes en Cloudflare R2 (S3). Solo usa {@code PutObject}, {@code DeleteObject(s)} y {@code ListObjectsV2}, que R2
 * documenta como soportadas. Cualquier fallo del servicio (red, credenciales, 5xx, tiempo agotado) se convierte en
 * {@link ImageStorageUnavailableException}: el contenido de la web nunca depende de este adaptador.
 */
@ApplicationScoped
public class R2FileStorageAdapter implements FileStoragePort {

    private final R2Settings settings;
    private final Supplier<S3Client> client;

    @Inject
    public R2FileStorageAdapter(R2Settings settings, Instance<S3Client> client) {
        this(settings, client::get);
    }

    public R2FileStorageAdapter(R2Settings settings, Supplier<S3Client> client) {
        this.settings = settings;
        this.client = client;
    }

    @Override
    public void put(String key, byte[] content, String contentType, String cacheControl) {
        call(s3 -> s3.putObject(
                PutObjectRequest.builder().bucket(bucket()).key(key).contentType(contentType).cacheControl(cacheControl).build(),
                RequestBody.fromBytes(content)));
    }

    @Override
    public void delete(String key) {
        call(s3 -> s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(key).build()));
    }

    @Override
    public void deleteAll(List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        if (keys.size() > MAX_DELETE_BATCH) {
            throw new IllegalArgumentException("At most " + MAX_DELETE_BATCH + " keys per call");
        }
        DeleteObjectsResponse response = call(s3 -> s3.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(bucket())
                .delete(Delete.builder()
                        .objects(keys.stream().map(key -> ObjectIdentifier.builder().key(key).build()).toList())
                        .quiet(true)
                        .build())
                .build()));
        if (response.hasErrors() && !response.errors().isEmpty()) {
            throw new ImageStorageUnavailableException("DeleteObjects failed for " + response.errors().size() + " key(s)");
        }
    }

    @Override
    public List<StoredObject> list(String prefix) {
        return call(s3 -> s3.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket()).prefix(prefix).build())
                .contents().stream()
                .map(o -> new StoredObject(o.key(), o.size(), o.lastModified()))
                .toList());
    }

    @Override
    public Optional<String> publicUrl(String key) {
        return settings.publicUrl().map(String::strip).filter(url -> !url.isEmpty())
                .map(url -> (url.endsWith("/") ? url.substring(0, url.length() - 1) : url) + "/" + key);
    }

    private String bucket() {
        return settings.bucket().orElseThrow();
    }

    private <T> T call(Function<S3Client, T> action) {
        if (!settings.configured()) {
            throw new ImageStorageUnavailableException("R2 is not configured");
        }
        try {
            return action.apply(client.get());
        } catch (SdkException e) {
            // Sin el mensaje del SDK en la respuesta al cliente: la excepción queda como causa, solo para el log.
            throw new ImageStorageUnavailableException(e.getClass().getSimpleName(), e);
        }
    }
}
