package dev.julioflores.cv.application;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Imágenes mínimas con cabecera real (solo las pruebas las usan), con relleno hasta el tamaño pedido. */
final class ImageSamples {

    private ImageSamples() {}

    static byte[] jpeg(int width, int height, int size) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xC0, 0, 11, 8});
        out.writeBytes(new byte[] {(byte) (height >> 8), (byte) height, (byte) (width >> 8), (byte) width, 3, 0, 0});
        while (out.size() < size) {
            out.write(0);
        }
        return out.toByteArray();
    }

    static byte[] png(int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'});
        out.writeBytes(new byte[] {(byte) (width >> 24), (byte) (width >> 16), (byte) (width >> 8), (byte) width});
        out.writeBytes(new byte[] {(byte) (height >> 24), (byte) (height >> 16), (byte) (height >> 8), (byte) height});
        return out.toByteArray();
    }

    static byte[] svg() {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
    }
}
