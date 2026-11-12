package dev.julioflores.cv.infrastructure;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/** Imágenes de verdad para las pruebas (PNG/JPEG/GIF las codifica el JDK; WebP se arma con su cabecera mínima). */
public final class ImageFiles {

    private ImageFiles() {}

    public static byte[] encode(String format, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] png(int width, int height) {
        return encode("png", width, height);
    }

    public static byte[] jpeg(int width, int height) {
        return encode("jpg", width, height);
    }

    public static byte[] gif(int width, int height) {
        return encode("gif", width, height);
    }

    /** WebP extendido (VP8X) con la cabecera correcta y relleno: basta para que se reconozca y se midan sus dimensiones. */
    public static byte[] webp(int width, int height) {
        byte[] out = new byte[64];
        System.arraycopy(new byte[] {'R', 'I', 'F', 'F', 56, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', 'X', 10, 0, 0, 0}, 0, out, 0, 20);
        int w = width - 1;
        int h = height - 1;
        out[24] = (byte) w;
        out[25] = (byte) (w >> 8);
        out[26] = (byte) (w >> 16);
        out[27] = (byte) h;
        out[28] = (byte) (h >> 8);
        out[29] = (byte) (h >> 16);
        return out;
    }

    /** Un archivo del tamaño pedido: una cabecera JPEG válida seguida de relleno. */
    public static byte[] jpegOfSize(int size) {
        byte[] base = jpeg(8, 8);
        byte[] out = new byte[Math.max(size, base.length)];
        System.arraycopy(base, 0, out, 0, base.length);
        return out;
    }
}
