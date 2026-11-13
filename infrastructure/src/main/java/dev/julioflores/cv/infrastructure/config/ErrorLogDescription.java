package dev.julioflores.cv.infrastructure.config;

import java.sql.SQLException;
import java.util.regex.Pattern;

/**
 * Cómo se registra un fallo inesperado. Un error de base de datos no vuelca ni su mensaje ni su traza: el mensaje de PostgreSQL
 * puede traer la fila entera ({@code Failing row contains (...)}) y la URL o credenciales de conexión. Se deja solo la clase y el
 * SQLState. Cualquier otra excepción conserva su traza completa. Vive en {@code config} (pública) para que la usen tanto el
 * mapeador de errores como los adaptadores de salida.
 */
public final class ErrorLogDescription {

    private static final int MAX_CAUSE_DEPTH = 20;
    private static final Pattern SQL_STATE = Pattern.compile("[0-9A-Z]{5}");

    private ErrorLogDescription() {}

    /** Mensaje del registro y excepción a adjuntar ({@code null} si no debe adjuntarse ninguna). */
    public record Described(String message, Throwable thrown) {}

    public static Described describe(Throwable error) {
        SQLException sql = findSql(error);
        if (sql == null) {
            return new Described("Unexpected error", error);
        }
        String state = sql.getSQLState() != null && SQL_STATE.matcher(sql.getSQLState()).matches() ? sql.getSQLState() : "-";
        return new Described("Unexpected database error (SQLState=" + state + ", class=" + sql.getClass().getSimpleName() + ")", null);
    }

    private static SQLException findSql(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof SQLException sql) {
                return sql;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return null;
    }
}
