package dev.julioflores.cv.domain;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** La clave de una imagen en el almacenamiento: {@code site/projects/<uuid>.<ext>}, generada siempre por el servidor. */
public final class ImageKey {

    public static final String PREFIX = "site/projects/";

    /** Un UUID en minúsculas: la parte variable de la clave y el {@code id} de {@code DELETE /images/{id}}. */
    private static final String UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private static final Pattern UUID_PATTERN = Pattern.compile(UUID_REGEX);
    private static final Pattern PATTERN = Pattern.compile("site/projects/(" + UUID_REGEX + ")\\.(jpg|png|webp|gif)");

    private ImageKey() {}

    public static String of(UUID id, ImageFormat format) {
        return PREFIX + id + "." + format.extension();
    }

    /** {@code true} si el texto es exactamente un UUID en minúsculas. */
    public static boolean isUuid(String id) {
        return id != null && UUID_PATTERN.matcher(id).matches();
    }

    /** {@code true} si la clave tiene exactamente esa forma (sin {@code ..}, sin otro prefijo, UUID en minúsculas). */
    public static boolean isValid(String key) {
        return key != null && PATTERN.matcher(key).matches();
    }

    /** El UUID de una clave válida, en minúsculas; vacío si la clave no es válida. */
    public static Optional<String> idOf(String key) {
        if (key == null) {
            return Optional.empty();
        }
        Matcher matcher = PATTERN.matcher(key);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
