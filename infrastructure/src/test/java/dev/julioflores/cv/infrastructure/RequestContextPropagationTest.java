package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El MDC {@code request_id} del handler Vert.x llega al hilo de eventos y a los hilos de trabajo, sin mezclarse entre peticiones. */
@QuarkusTest
class RequestContextPropagationTest {

    private LogRecords logs;

    @BeforeEach
    void capture() {
        logs = LogRecords.attach();
    }

    @AfterEach
    void release() {
        logs.close();
    }

    private List<ExtLogRecord> probe(String tag, int expected) {
        List<ExtLogRecord> found = logs.await(r -> LogProbeResource.LOGGER.equals(r.getLoggerName())
                && r.getFormattedMessage().endsWith("tag=" + tag) && countFor(tag) >= expected);
        return found;
    }

    private long countFor(String tag) {
        return logs.byLogger(LogProbeResource.LOGGER).stream().filter(r -> r.getFormattedMessage().endsWith("tag=" + tag)).count();
    }

    @Test
    void theEventLoopAndTheWorkerThreadBothCarryTheRequestId() { // T11
        String id = "probe-" + UUID.randomUUID();
        given().header("X-Request-Id", id).queryParam("tag", "t11").when().get("/test/log-probe").then().statusCode(200);

        List<ExtLogRecord> records = probe("t11", 2);
        ExtLogRecord eventLoop = records.stream().filter(r -> r.getFormattedMessage().startsWith("probe-event-loop")).findFirst().orElseThrow();
        ExtLogRecord worker = records.stream().filter(r -> r.getFormattedMessage().startsWith("probe-worker")).findFirst().orElseThrow();
        assertTrue(eventLoop.getThreadName().contains("eventloop"), eventLoop.getThreadName());
        assertFalse(worker.getThreadName().contains("eventloop"), worker.getThreadName());
        assertEquals(id, LogRecords.mdc(eventLoop, "request_id"));
        assertEquals(id, LogRecords.mdc(worker, "request_id"));
    }

    @Test
    void aBlockingResourceMethodAlsoCarriesTheRequestId() { // T11 (variante @Blocking)
        String id = "probe-" + UUID.randomUUID();
        given().header("X-Request-Id", id).queryParam("tag", "t11b").when().get("/test/log-probe/blocking").then().statusCode(200);
        ExtLogRecord record = probe("t11b", 1).get(0);
        assertFalse(record.getThreadName().contains("eventloop"), record.getThreadName());
        assertEquals(id, LogRecords.mdc(record, "request_id"));
    }

    @Test
    void twentyConcurrentRequestsNeverMixTheirIds() throws Exception { // T12
        int requests = 20;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            String tag = "c" + i;
            String id = "conc-" + i + "-" + UUID.randomUUID();
            futures.add(pool.submit(() -> {
                go.await();
                given().header("X-Request-Id", id).queryParam("tag", tag).when().get("/test/log-probe").then().statusCode(200);
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        logs.await(r -> LogProbeResource.LOGGER.equals(r.getLoggerName()) && logs.byLogger(LogProbeResource.LOGGER).size() >= requests * 2);
        for (int i = 0; i < requests; i++) {
            String suffix = "tag=c" + i;
            List<ExtLogRecord> mine = logs.byLogger(LogProbeResource.LOGGER).stream()
                    .filter(r -> r.getFormattedMessage().endsWith(suffix)).toList();
            assertEquals(2, mine.size(), suffix);
            String id = LogRecords.mdc(mine.get(0), "request_id");
            assertTrue(id.startsWith("conc-" + i + "-"), id + " for " + suffix);
            assertEquals(id, LogRecords.mdc(mine.get(1), "request_id"), suffix);
        }
    }
}
