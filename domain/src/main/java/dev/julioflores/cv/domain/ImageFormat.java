package dev.julioflores.cv.domain;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Formatos de imagen que se aceptan al subir. El formato sale SOLO de los bytes (firma al inicio del archivo): el nombre y el
 * tipo que declare el cliente no cuentan nunca. SVG queda fuera a propósito (puede llevar script).
 */
public enum ImageFormat {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp"),
    GIF("gif", "image/gif");

    /** Bytes mínimos para reconocer un formato (la firma más larga, la de WebP, ocupa 12). */
    public static final int HEADER_BYTES = 12;

    private final String extension;
    private final String mediaType;

    ImageFormat(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    /** Extensión canónica, sin punto (también la de la clave en el almacenamiento). */
    public String extension() {
        return extension;
    }

    public String mediaType() {
        return mediaType;
    }

    /** El formato por los bytes mágicos, o vacío si no es uno soportado (o hay menos de {@value #HEADER_BYTES} bytes). */
    public static Optional<ImageFormat> detect(byte[] content) {
        if (content == null || content.length < HEADER_BYTES) {
            return Optional.empty();
        }
        if (startsWith(content, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, 'G', 'I', 'F', '8') && (content[4] == '7' || content[4] == '9') && content[5] == 'a') {
            return Optional.of(GIF);
        }
        if (startsWith(content, 'R', 'I', 'F', 'F') && content[8] == 'W' && content[9] == 'E' && content[10] == 'B'
                && content[11] == 'P') {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    /**
     * Si los bytes son de OTRO tipo de imagen conocido pero no admitido (SVG, HEIC/AVIF, BMP, TIFF, ICO): permite distinguir
     * «formato no soportado» de «esto no es una imagen».
     */
    public static boolean isKnownUnsupported(byte[] content) {
        if (content == null || content.length < 4) {
            return false;
        }
        if (startsWith(content, 'B', 'M') || startsWith(content, 'I', 'I', 0x2A, 0x00)
                || startsWith(content, 'M', 'M', 0x00, 0x2A) || startsWith(content, 0x00, 0x00, 0x01, 0x00)) {
            return true;
        }
        if (content.length >= 12 && content[4] == 'f' && content[5] == 't' && content[6] == 'y' && content[7] == 'p') {
            String brand = new String(content, 8, 4, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
            if (Arrays.asList("heic", "heix", "hevc", "heim", "heis", "mif1", "msf1", "avif", "avis").contains(brand)) {
                return true;
            }
        }
        String head = new String(content, 0, Math.min(content.length, 1024), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return head.contains("<svg");
    }

    private static boolean startsWith(byte[] content, int... signature) {
        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
