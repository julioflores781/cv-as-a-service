package dev.julioflores.cv.infrastructure.config;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * El cliente S3 contra R2. Los ajustes salen de la documentación de Cloudflare (endpoint por cuenta, región {@code auto},
 * path-style, sin {@code aws-chunked}) y de que el SDK desde 2.30 calcula checksums por defecto, algo que R2 puede rechazar:
 * se fija {@code WHEN_REQUIRED}. Si R2 devolviera 400/403 al subir, el guion de {@code impl.md} lista qué tocar.
 */
public class R2ClientConfig {

    public static final String REGION = "auto";

    /** Solo se calcula el checksum cuando la operación lo exige ({@code DeleteObjects}), no en cada subida. */
    public static final RequestChecksumCalculation REQUEST_CHECKSUM = RequestChecksumCalculation.WHEN_REQUIRED;

    public static final ResponseChecksumValidation RESPONSE_CHECKSUM = ResponseChecksumValidation.WHEN_REQUIRED;

    /** Un reintento (2 intentos en total). */
    public static final int MAX_ATTEMPTS = 2;

    @Produces
    @Singleton
    S3Client s3Client(R2Settings settings) {
        return build(settings);
    }

    /** La configuración de servicio: path-style (el bucket va en la ruta) y sin {@code aws-chunked} (R2 da 403 por firma). */
    static S3Configuration serviceConfiguration() {
        return S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build();
    }

    public static S3Client build(R2Settings settings) {
        if (!settings.configured()) {
            throw new IllegalStateException("R2 is not configured");
        }
        return S3Client.builder()
                .endpointOverride(URI.create(settings.endpoint().orElseThrow()))
                .region(Region.of(REGION))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        settings.accessKeyId().orElseThrow(), settings.secretAccessKey().orElseThrow())))
                .serviceConfiguration(serviceConfiguration())
                .requestChecksumCalculation(REQUEST_CHECKSUM)
                .responseChecksumValidation(RESPONSE_CHECKSUM)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(settings.connectTimeout())
                        .socketTimeout(settings.requestTimeout()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(settings.requestTimeout())
                        .apiCallTimeout(settings.requestTimeout().multipliedBy(MAX_ATTEMPTS).plus(settings.connectTimeout()))
                        .retryStrategy(retry -> retry.maxAttempts(MAX_ATTEMPTS))
                        .build())
                .build();
    }
}
