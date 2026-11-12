package dev.julioflores.cv.domain;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Cabeceras mínimas pero reales de cada formato (solo las pruebas las usan), con relleno hasta el tamaño pedido. */
final class ImageSamples {

    private ImageSamples() {}

    static byte[] png(int width, int height, int size) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'});
        out.writeBytes(be32(width));
        out.writeBytes(be32(height));
        return pad(out, size);
    }

    static byte[] gif(int width, int height, int size) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("GIF89a".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(le16(width));
        out.writeBytes(le16(height));
        return pad(out, size);
    }

    /** JPEG con un segmento APP0 antes del SOF0, para que el lector tenga que saltar segmentos. */
    static byte[] jpeg(int width, int height, int size) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 4, 0, 0});
        out.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xC0, 0, 11, 8});
        out.writeBytes(new byte[] {(byte) (height >> 8), (byte) height, (byte) (width >> 8), (byte) width});
        return pad(out, size);
    }

    /** WebP extendido (VP8X): el ancho y el alto van menos uno, en 24 bits. */
    static byte[] webp(int width, int height, int size) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(new byte[] {0, 0, 0, 0});
        out.writeBytes("WEBPVP8X".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(new byte[] {10, 0, 0, 0, 0, 0, 0, 0});
        out.writeBytes(le24(width - 1));
        out.writeBytes(le24(height - 1));
        return pad(out, size);
    }

    private static byte[] pad(ByteArrayOutputStream out, int size) {
        while (out.size() < size) {
            out.write(0);
        }
        return out.toByteArray();
    }

    private static byte[] be32(int v) {
        return new byte[] {(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v};
    }

    private static byte[] le16(int v) {
        return new byte[] {(byte) v, (byte) (v >> 8)};
    }

    private static byte[] le24(int v) {
        return new byte[] {(byte) v, (byte) (v >> 8), (byte) (v >> 16)};
    }
}
