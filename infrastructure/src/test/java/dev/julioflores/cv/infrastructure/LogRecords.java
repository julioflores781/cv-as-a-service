package dev.julioflores.cv.infrastructure;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.jboss.logmanager.ExtLogRecord;

/**
 * Captura de logs para las pruebas: un {@link Handler} JUL enganchado al logger raíz (el LogManager de JBoss es JUL; {@code LogCapture}
 * no existe en esta versión de Quarkus). Se usa con {@code try (LogRecords logs = LogRecords.attach())} o con {@code attach()} en
 * un {@code @BeforeEach} y {@code close()} en un {@code @AfterEach}.
 *
 * <p>La línea de access se escribe al cerrar la respuesta, o sea después de que el cliente ya la tiene: se espera con {@link #await}
 * (polling de 10 ms con tope), nunca con un {@code sleep} fijo.
 */
public final class LogRecords extends Handler implements AutoCloseable {

    static final String ACCESS = "access";

    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(5);
    private static final long POLL_MILLIS = 10;

    private final List<ExtLogRecord> records = new CopyOnWriteArrayList<>();
    private final java.util.logging.Logger root = java.util.logging.Logger.getLogger("");

    private LogRecords() {}

    public static LogRecords attach() {
        LogRecords handler = new LogRecords();
        handler.root.addHandler(handler);
        return handler;
    }

    @Override
    public void publish(LogRecord record) {
        ExtLogRecord ext = record instanceof ExtLogRecord e ? e : ExtLogRecord.wrap(record);
        // El MDC se lee del hilo que escribe: hay que copiarlo ahora, no cuando el test inspeccione el registro.
        ext.copyAll();
        records.add(ext);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {
        root.removeHandler(this);
    }

    public List<ExtLogRecord> all() {
        return List.copyOf(records);
    }

    public List<ExtLogRecord> byLogger(String loggerName) {
        return records.stream().filter(r -> loggerName.equals(r.getLoggerName())).toList();
    }

    /** Los mensajes formateados de la categoría {@code access}. */
    public List<String> accessLines() {
        return byLogger(ACCESS).stream().map(ExtLogRecord::getFormattedMessage).toList();
    }

    /** Espera (con polling) a que haya al menos un registro que cumpla el predicado y devuelve todos los que lo cumplen. */
    public List<ExtLogRecord> await(Predicate<ExtLogRecord> predicate) {
        return await(predicate, DEFAULT_WAIT);
    }

    public List<ExtLogRecord> await(Predicate<ExtLogRecord> predicate, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            List<ExtLogRecord> found = records.stream().filter(predicate).toList();
            if (!found.isEmpty()) {
                return found;
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("No log record matched within " + timeout + ". Captured:\n" + dump());
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for a log record", e);
            }
        }
    }

    /** Espera a la línea de access que empieza por el prefijo (p. ej. {@code "GET /api/v1/profile 200"}) y trae este {@code rid}. */
    public String awaitAccessLine(String prefix, String requestId) {
        List<ExtLogRecord> found = await(r -> ACCESS.equals(r.getLoggerName())
                && r.getFormattedMessage().startsWith(prefix + " ")
                && r.getFormattedMessage().contains(" rid=" + requestId + " "));
        return found.get(0).getFormattedMessage();
    }

    /** Todo lo que cada registro podría volcar (categoría, nivel, mensaje, parámetros, MDC, traza y causas), para búsquedas negativas. */
    public String dump() {
        StringBuilder out = new StringBuilder();
        for (ExtLogRecord r : records) {
            out.append(r.getLevel()).append(' ').append(r.getLoggerName()).append(' ').append(r.getFormattedMessage()).append('\n');
            out.append(" message=").append(r.getMessage()).append('\n');
            if (r.getParameters() != null) {
                out.append(" params=").append(java.util.Arrays.toString(r.getParameters())).append('\n');
            }
            Map<String, String> mdc = r.getMdcCopy();
            out.append(" mdc=").append(new java.util.TreeMap<>(mdc)).append('\n');
            if (r.getThrown() != null) {
                StringWriter trace = new StringWriter();
                r.getThrown().printStackTrace(new PrintWriter(trace));
                out.append(" thrown=").append(trace).append('\n');
            }
        }
        return out.toString();
    }

    /** El valor MDC del registro, o {@code null}. */
    public static String mdc(ExtLogRecord record, String key) {
        return record.getMdc(key);
    }

    public static List<String> messages(List<ExtLogRecord> records) {
        List<String> out = new ArrayList<>();
        records.forEach(r -> out.add(r.getFormattedMessage()));
        return out;
    }
}
