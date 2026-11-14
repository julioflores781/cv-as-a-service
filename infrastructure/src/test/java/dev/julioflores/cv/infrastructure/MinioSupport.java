package dev.julioflores.cv.infrastructure;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Un MinIO compartido por todas las pruebas de la JVM (se arranca la primera vez que alguien lo pide) y un cliente propio de la
 * prueba para mirar el bucket sin pasar por el adaptador que se prueba.
 *
 * <p>La imagen de MinIO ya no se publica con etiquetas fijas en Docker Hub ni en Quay; se usa la de Chainguard (Docker
 * pull sin credenciales). Se puede cambiar con {@code -Dcv.test.minio-image=...}.
 */
public final class MinioSupport {

    public static final String BUCKET = "cv-test-media";
    public static final String PUBLIC_URL = "https://media.example.test";

    private static final String IMAGE = System.getProperty("cv.test.minio-image", "cgr.dev/chainguard/minio:latest");
    private static final String USER = "cv-test-user";
    private static final String PASSWORD = "cv-test-password-0123456789";

    private static MinIOContainer container;
    private static S3Client client;

    private MinioSupport() {}

    public static synchronized MinIOContainer container() {
        if (container == null) {
            MinIOContainer started = new MinIOContainer(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("minio/minio"))
                    .withUserName(USER)
                    .withPassword(PASSWORD);
            started.start();
            container = started;
            client().createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        }
        return container;
    }

    public static String endpoint() {
        return container().getS3URL();
    }

    public static String user() {
        return USER;
    }

    public static String password() {
        return PASSWORD;
    }

    /** Cliente de la prueba (path-style, sin nada de R2) para sembrar y comprobar objetos directamente. */
    public static synchronized S3Client client() {
        if (client == null) {
            client = S3Client.builder()
                    .endpointOverride(URI.create(container().getS3URL()))
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(USER, PASSWORD)))
                    .forcePathStyle(true)
                    .httpClientBuilder(UrlConnectionHttpClient.builder())
                    .build();
        }
        return client;
    }

    /** Las claves del bucket que empiezan por el prefijo. */
    public static List<String> keys(String prefix) {
        List<String> keys = new ArrayList<>();
        client().listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(BUCKET).prefix(prefix).build())
                .contents().forEach(o -> keys.add(o.key()));
        return keys;
    }

    public static void put(String key, byte[] content) {
        client().putObject(PutObjectRequest.builder().bucket(BUCKET).key(key).build(), RequestBody.fromBytes(content));
    }

    public static List<S3Object> objects(String prefix) {
        List<S3Object> objects = new ArrayList<>();
        client().listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(BUCKET).prefix(prefix).build())
                .contents().forEach(objects::add);
        return objects;
    }

    /** Vacía el bucket (cada prueba parte de cero). */
    public static void clear() {
        List<String> keys = keys("");
        for (int from = 0; from < keys.size(); from += 1000) {
            List<ObjectIdentifier> batch = keys.subList(from, Math.min(keys.size(), from + 1000)).stream()
                    .map(k -> ObjectIdentifier.builder().key(k).build()).toList();
            client().deleteObjects(DeleteObjectsRequest.builder().bucket(BUCKET)
                    .delete(Delete.builder().objects(batch).build()).build());
        }
    }
}
