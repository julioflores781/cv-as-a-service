package dev.julioflores.cv.domain;

import java.util.Optional;

/** Ancho y alto en píxeles de una imagen, leídos de su cabecera (sin decodificarla). */
public record ImageDimensions(int width, int height) {

    public ImageDimensions {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image dimensions must be positive");
        }
    }

    /** Las dimensiones según el formato, o vacío si la cabecera está truncada o es incoherente. */
    public static Optional<ImageDimensions> read(ImageFormat format, byte[] c) {
        try {
            return switch (format) {
                case PNG -> c.length >= 24 ? of(be32(c, 16), be32(c, 20)) : Optional.empty();
                case GIF -> c.length >= 10 ? of(le16(c, 6), le16(c, 8)) : Optional.empty();
                case JPEG -> jpeg(c);
                case WEBP -> webp(c);
            };
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Optional<ImageDimensions> jpeg(byte[] c) {
        int i = 2;
        while (i + 9 < c.length) {
            if ((c[i] & 0xFF) != 0xFF) {
                i++;
                continue;
            }
            int marker = c[i + 1] & 0xFF;
            if (marker == 0xFF) {
                i++;
                continue;
            }
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                i += 2;
                continue;
            }
            boolean startOfFrame = marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            if (startOfFrame) {
                return of(be16(c, i + 7), be16(c, i + 5));
            }
            i += 2 + be16(c, i + 2);
        }
        return Optional.empty();
    }

    private static Optional<ImageDimensions> webp(byte[] c) {
        String chunk = new String(c, 12, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
        return switch (chunk) {
            case "VP8 " -> of(le16(c, 26) & 0x3FFF, le16(c, 28) & 0x3FFF);
            case "VP8L" -> {
                long bits = le32(c, 21);
                yield of((int) (bits & 0x3FFF) + 1, (int) ((bits >> 14) & 0x3FFF) + 1);
            }
            case "VP8X" -> of(le24(c, 24) + 1, le24(c, 27) + 1);
            default -> Optional.empty();
        };
    }

    private static Optional<ImageDimensions> of(int width, int height) {
        return width > 0 && height > 0 ? Optional.of(new ImageDimensions(width, height)) : Optional.empty();
    }

    private static int be16(byte[] c, int at) {
        return (c[at] & 0xFF) << 8 | (c[at + 1] & 0xFF);
    }

    private static int be32(byte[] c, int at) {
        return (be16(c, at) << 16) | be16(c, at + 2);
    }

    private static int le16(byte[] c, int at) {
        return (c[at] & 0xFF) | (c[at + 1] & 0xFF) << 8;
    }

    private static int le24(byte[] c, int at) {
        return le16(c, at) | (c[at + 2] & 0xFF) << 16;
    }

    private static long le32(byte[] c, int at) {
        return (le16(c, at) & 0xFFFFL) | (long) le16(c, at + 2) << 16;
    }
}
