package dev.julioflores.cv.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Peticiones HTTP/1.1 escritas a mano por un socket: control total de los bytes de las cabeceras y acceso a las de la respuesta. */
final class RawResponses {

    /** Estado y cabeceras (en minúsculas) de la respuesta. */
    record Head(int status, Map<String, String> headers, String text) {}

    private RawResponses() {}

    /** Un {@code GET} con las líneas de cabecera dadas (cada una ya con su {@code Nombre: valor}, en bytes arbitrarios). */
    static Head get(URI base, String path, byte[]... headerLines) throws IOException {
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        request.write(("GET " + path + " HTTP/1.1\r\nHost: " + base.getHost() + ":" + base.getPort() + "\r\nConnection: close\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        for (byte[] line : headerLines) {
            request.write(line);
            request.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        request.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
        return exchange(base, request.toByteArray());
    }

    /**
     * Un {@code POST} que declara {@code Content-Length: declared} pero no envía cuerpo: el servidor debe rechazarlo (413) por la
     * longitud declarada, sin esperar a leer nada.
     */
    static Head postDeclaringLength(URI base, String path, String contentType, long declared) throws IOException {
        String request = "POST " + path + " HTTP/1.1\r\nHost: " + base.getHost() + ":" + base.getPort()
                + "\r\nConnection: close\r\nContent-Type: " + contentType + "\r\nContent-Length: " + declared + "\r\n\r\n";
        return exchange(base, request.getBytes(StandardCharsets.ISO_8859_1));
    }

    static byte[] header(String name, String value) {
        return (name + ": " + value).getBytes(StandardCharsets.UTF_8);
    }

    private static Head exchange(URI base, byte[] request) throws IOException {
        try (Socket socket = new Socket(base.getHost(), base.getPort())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(request);
            socket.getOutputStream().flush();
            String response = new String(readAll(socket.getInputStream()), StandardCharsets.ISO_8859_1);
            String[] lines = response.split("\r\n");
            int status = Integer.parseInt(lines[0].split(" ")[1]);
            Map<String, String> headers = new LinkedHashMap<>();
            for (int i = 1; i < lines.length && !lines[i].isEmpty(); i++) {
                int colon = lines[i].indexOf(':');
                headers.put(lines[i].substring(0, colon).toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
            }
            return new Head(status, headers, response);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try {
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                if (new String(out.toByteArray(), StandardCharsets.ISO_8859_1).contains("\r\n\r\n")) {
                    break; // solo hacen falta el estado y las cabeceras
                }
            }
        } catch (java.net.SocketException | java.net.SocketTimeoutException e) {
            // El servidor cerró la conexión (Connection: close) o no hay más datos: lo leído basta.
            // (cuando el servidor cierra antes de terminar, lo leído basta)
        }
        return out.toByteArray();
    }
}
