package dev.julioflores.cv.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.julioflores.cv.domain.ContactMessage;
import dev.julioflores.cv.infrastructure.LogRecords;
import dev.julioflores.cv.infrastructure.adapter.out.telegram.TelegramContactNotifier;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.jboss.logging.MDC;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T60. Los registros del notificador contra un "Telegram" local: el callback corre en un hilo del HttpClient, así que se espera
 * por polling ({@link LogRecords#await}) y nunca con un sleep fijo. El token es un canario ficticio.
 */
@QuarkusTest
class TelegramNotifierLoggingTest {

    private static final String LOGGER = TelegramContactNotifier.class.getName();
    private static final String CANARY_TOKEN = "CANARY-BOT";
    private static final String CANARY_NAME = "Canary Nombre";
    private static final String CANARY_BODY = "Canary cuerpo del mensaje";
    private static final ContactMessage MESSAGE = new ContactMessage(CANARY_NAME, "canary@example.com", CANARY_BODY);

    @Inject
    ObjectMapper mapper;

    private HttpServer server;
    private final BlockingQueue<String> paths = new LinkedBlockingQueue<>();
    private volatile int status = 200;

    @BeforeEach
    void startFakeTelegram() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        MDC.remove("request_id");
    }

    private TelegramContactNotifier notifier(String apiUrl) {
        return new TelegramContactNotifier(mapper, Optional.of(CANARY_TOKEN), Optional.of("4242"), apiUrl);
    }

    private TelegramContactNotifier notifier() {
        return notifier("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /** Envía con un request_id en el MDC del hilo de la petición y espera el registro de resultado (INFO o WARN) con ese id. */
    private ExtLogRecord send(TelegramContactNotifier notifier, LogRecords logs, String requestId) {
        MDC.put("request_id", requestId);
        notifier.notify(MESSAGE);
        MDC.remove("request_id");
        return logs.await(r -> LOGGER.equals(r.getLoggerName()) && requestId.equals(LogRecords.mdc(r, "request_id"))).get(0);
    }

    private static void assertNoLeak(LogRecords logs) {
        String dump = logs.dump();
        for (String leak : new String[] {CANARY_TOKEN, CANARY_NAME, "canary@example.com", CANARY_BODY, "127.0.0.1", "http://", "sendMessage"}) {
            assertFalse(dump.contains(leak), leak + " in " + dump);
        }
    }

    @Test
    void a200LeavesAnInfoSentWithTheRequestIdOfTheOriginalRequestOnTheCallbackThread() throws Exception {
        String rid = "tg-ok-" + System.nanoTime();
        try (LogRecords logs = LogRecords.attach()) {
            ExtLogRecord line = send(notifier(), logs, rid);
            assertEquals(Level.INFO.intValue(), line.getLevel().intValue());
            assertEquals("Telegram notification sent", line.getFormattedMessage());
            assertEquals("/bot" + CANARY_TOKEN + "/sendMessage", paths.poll(5, TimeUnit.SECONDS)); // el canario sí viaja en la URL
            assertNotEquals(Thread.currentThread().getName(), line.getThreadName(), "el callback es de otro hilo");
            assertNoLeak(logs);
        }
    }

    @Test
    void a401And500LeaveAWarnWithOnlyTheHttpCode() {
        try (LogRecords logs = LogRecords.attach()) {
            status = 401;
            ExtLogRecord unauthorized = send(notifier(), logs, "tg-401-" + System.nanoTime());
            status = 500;
            ExtLogRecord failing = send(notifier(), logs, "tg-500-" + System.nanoTime());
            assertEquals(Level.WARNING.intValue(), unauthorized.getLevel().intValue());
            assertEquals("Telegram notification rejected with HTTP 401", unauthorized.getFormattedMessage());
            assertEquals(Level.WARNING.intValue(), failing.getLevel().intValue());
            assertEquals("Telegram notification rejected with HTTP 500", failing.getFormattedMessage());
            assertNull(unauthorized.getThrown());
            assertNull(failing.getThrown());
            assertNoLeak(logs);
        }
    }

    @Test
    void aDownServerLeavesAWarnWithOnlyTheErrorClass() {
        try (LogRecords logs = LogRecords.attach()) {
            ExtLogRecord line = send(notifier("http://127.0.0.1:1"), logs, "tg-down-" + System.nanoTime());
            assertEquals(Level.WARNING.intValue(), line.getLevel().intValue());
            assertTrue(line.getFormattedMessage().matches("Telegram notification failed: [A-Za-z]+Exception"), line.getFormattedMessage());
            assertNull(line.getThrown());
            assertNoLeak(logs);
        }
    }

    @Test
    void aMalformedUrlLeavesAWarnWithOnlyTheErrorClassOnTheCallingThread() {
        try (LogRecords logs = LogRecords.attach()) {
            ExtLogRecord line = send(notifier("http://[bad"), logs, "tg-bad-" + System.nanoTime());
            assertTrue(line.getFormattedMessage().matches("Telegram notification could not be sent: [A-Za-z]+Exception"),
                    line.getFormattedMessage());
            assertNoLeak(logs);
        }
    }

    @Test
    void withoutConfigurationOnlyADebugLineIsWrittenAndNothingIsSent() throws Exception {
        java.util.logging.Logger jul = java.util.logging.Logger.getLogger(LOGGER);
        Level before = jul.getLevel();
        jul.setLevel(Level.FINE);
        try (LogRecords logs = LogRecords.attach()) {
            new TelegramContactNotifier(mapper, Optional.empty(), Optional.of("1"), "http://127.0.0.1:1").notify(MESSAGE);
            new TelegramContactNotifier(mapper, Optional.of(CANARY_TOKEN), Optional.empty(), "http://127.0.0.1:1").notify(MESSAGE);
            List<ExtLogRecord> lines = logs.byLogger(LOGGER);
            assertEquals(2, lines.size(), logs.dump());
            lines.forEach(r -> assertEquals(Level.FINE.intValue(), r.getLevel().intValue(), logs.dump()));
            assertNoLeak(logs);
            assertNull(paths.poll(200, TimeUnit.MILLISECONDS));
        } finally {
            jul.setLevel(before);
        }
    }

    // Extra fuera del plan: dos peticiones seguidas no se contagian el id aunque el pool de HttpClient reutilice hilos
    @Test
    void eachCallbackCarriesItsOwnRequestIdAndLeavesNoneBehind() {
        try (LogRecords logs = LogRecords.attach()) {
            for (int i = 0; i < 5; i++) {
                String rid = "tg-seq-" + i + "-" + System.nanoTime();
                ExtLogRecord line = send(notifier(), logs, rid);
                assertEquals(rid, LogRecords.mdc(line, "request_id"));
            }
            long sent = logs.byLogger(LOGGER).stream().filter(r -> r.getFormattedMessage().equals("Telegram notification sent")).count();
            assertEquals(5, sent);
            assertEquals(5, logs.byLogger(LOGGER).stream().map(r -> LogRecords.mdc(r, "request_id")).distinct().count());
        }
    }

    // Extra fuera del plan: sin request_id en el hilo de origen (p. ej. fuera de una petición) el callback tampoco inventa uno
    @Test
    void withoutARequestIdTheCallbackLineHasNone() {
        try (LogRecords logs = LogRecords.attach()) {
            notifier().notify(MESSAGE);
            ExtLogRecord line = logs.await(r -> LOGGER.equals(r.getLoggerName())).get(0);
            assertNotNull(line);
            assertNull(LogRecords.mdc(line, "request_id"));
        }
    }
}
