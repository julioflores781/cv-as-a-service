package dev.julioflores.cv.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.infrastructure.MinioSupport;
import dev.julioflores.cv.infrastructure.MinioTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/** T125: la configuración del cliente S3 contra R2 (región, path-style, sin aws-chunked, checksums, tiempos y reintento). */
@QuarkusTest
@QuarkusTestResource(value = MinioTestResource.class, restrictToAnnotatedClass = true)
class R2ClientConfigTest {

    @Inject
    S3Client client;

    @Inject
    R2Settings settings;

    @Test
    void theClientUsesRegionAutoThePathStyleNoChunkedEncodingAndChecksumsOnlyWhenRequired() { // T125
        assertEquals("auto", client.serviceClientConfiguration().region().id());
        assertEquals(MinioSupport.endpoint(), client.serviceClientConfiguration().endpointOverride().orElseThrow().toString());

        S3Configuration service = R2ClientConfig.serviceConfiguration();
        assertTrue(service.pathStyleAccessEnabled());
        assertFalse(service.chunkedEncodingEnabled());

        assertEquals(RequestChecksumCalculation.WHEN_REQUIRED, R2ClientConfig.REQUEST_CHECKSUM);
        assertEquals(ResponseChecksumValidation.WHEN_REQUIRED, R2ClientConfig.RESPONSE_CHECKSUM);
        assertEquals("auto", R2ClientConfig.REGION);
    }

    @Test
    void timeoutsAreExplicitAndThereIsOneRetry() {
        assertEquals(Duration.ofSeconds(5), settings.connectTimeout());
        assertEquals(Duration.ofSeconds(10), settings.requestTimeout());
        var override = client.serviceClientConfiguration().overrideConfiguration();
        assertEquals(Optional.of(Duration.ofSeconds(10)), override.apiCallAttemptTimeout());
        assertEquals(Optional.of(Duration.ofSeconds(25)), override.apiCallTimeout());
        assertEquals(2, R2ClientConfig.MAX_ATTEMPTS);
        assertEquals(2, override.retryStrategy().orElseThrow().maxAttempts());
    }

    @Test
    void buildingWithoutConfigurationFailsClearly() {
        R2Settings none = new R2Settings() {
            public Optional<String> endpoint() { return Optional.empty(); }
            public Optional<String> accessKeyId() { return Optional.empty(); }
            public Optional<String> secretAccessKey() { return Optional.empty(); }
            public Optional<String> bucket() { return Optional.empty(); }
            public Optional<String> publicUrl() { return Optional.empty(); }
            public Duration connectTimeout() { return Duration.ofSeconds(1); }
            public Duration requestTimeout() { return Duration.ofSeconds(1); }
        };
        assertFalse(none.configured());
        assertThrows(IllegalStateException.class, () -> R2ClientConfig.build(none));
    }
}
