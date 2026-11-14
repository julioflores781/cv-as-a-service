package dev.julioflores.cv.infrastructure;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;

/** Peticiones con control del encabezado de longitud: con {@code Content-Length} (cuerpo conocido) o {@code chunked} (sin longitud). */
final class RawHttp {

    private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private RawHttp() {}

    /** Cuerpo de longitud conocida: el cliente envía {@code Content-Length}. */
    static int postWithLength(URI uri, String contentType, byte[] body) {
        return send(HttpRequest.newBuilder(uri).header("Content-Type", contentType).POST(BodyPublishers.ofByteArray(body)).build());
    }

    /** Como {@link #postWithLength} pero con una cabecera {@code Authorization}. */
    static int postWithLengthAuthorized(URI uri, String contentType, String authorization, byte[] body) {
        return send(HttpRequest.newBuilder(uri).header("Content-Type", contentType).header("Authorization", authorization)
                .POST(BodyPublishers.ofByteArray(body)).build());
    }

    /** Longitud desconocida: el cliente envía {@code Transfer-Encoding: chunked} y ningún {@code Content-Length}. */
    static int postChunked(URI uri, String contentType, byte[] body) {
        return send(HttpRequest.newBuilder(uri).header("Content-Type", contentType)
                .POST(BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body))).build());
    }

    /** Como {@link #postChunked} pero con una cabecera {@code Authorization}. */
    static int postChunkedAuthorized(URI uri, String contentType, String authorization, byte[] body) {
        return send(HttpRequest.newBuilder(uri).header("Content-Type", contentType).header("Authorization", authorization)
                .POST(BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body))).build());
    }

    private static int send(HttpRequest request) {
        try {
            return CLIENT.send(request, BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            // Con un cuerpo enorme el servidor responde 413 y cierra la conexión sin esperar el resto: según el momento del corte el
            // cliente recibe la respuesta o ve la conexión cerrada. Las dos cosas son «rechazado» (la pruebas de los tests de
            // límite miran además que el servidor no guardó nada), y devolver 413 aquí no enmascara un 500, que sí llegaría como estado.
            return 413;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
