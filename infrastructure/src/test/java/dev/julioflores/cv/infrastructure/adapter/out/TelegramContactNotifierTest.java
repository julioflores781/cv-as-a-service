package dev.julioflores.cv.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.julioflores.cv.domain.ContactMessage;
import dev.julioflores.cv.infrastructure.adapter.out.telegram.TelegramContactNotifier;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** @QuarkusTest para que JaCoCo mida la clase; el "Telegram" de estas pruebas es un servidor local. */
@QuarkusTest
class TelegramContactNotifierTest {

    private static final ContactMessage MESSAGE =
            new ContactMessage("Ada Ejemplo", "ada@example.com", "Hola, quiero hablar contigo.");

    private record Received(String path, String contentType, String body) {}

    @Inject
    ObjectMapper mapper;

    private HttpServer server;
    private final BlockingQueue<Received> received = new LinkedBlockingQueue<>();
    private int status = 200;

    @BeforeEach
    void startFakeTelegram() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            received.add(new Received(exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private TelegramContactNotifier notifier(Optional<String> token, Optional<String> chat) {
        return new TelegramContactNotifier(mapper, token, chat, "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Test
    void sendsTheMessageToTheBotApiWithTheChatAndTheContent() throws Exception {
        notifier(Optional.of("TOKEN123"), Optional.of("4242")).notify(MESSAGE);
        Received request = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(request, "Telegram no recibió la llamada");
        assertEquals("/botTOKEN123/sendMessage", request.path());
        assertTrue(request.contentType().startsWith("application/json"));
        JsonNode json = mapper.readTree(request.body());
        assertEquals("4242", json.get("chat_id").asText());
        String text = json.get("text").asText();
        assertTrue(text.contains("Ada Ejemplo <ada@example.com>"));
        assertTrue(text.contains("Hola, quiero hablar contigo."));
    }

    @Test
    void longMessagesAreTruncatedToTelegramsLimit() throws Exception {
        ContactMessage big = new ContactMessage("Ada", "ada@example.com", "x".repeat(5000));
        notifier(Optional.of("T"), Optional.of("1")).notify(big);
        Received request = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals(4000, mapper.readTree(request.body()).get("text").asText().length());
    }

    @Test
    void doesNothingWhenTokenOrChatAreMissingOrBlank() throws Exception {
        notifier(Optional.empty(), Optional.of("1")).notify(MESSAGE);
        notifier(Optional.of("T"), Optional.empty()).notify(MESSAGE);
        notifier(Optional.of("  "), Optional.of("1")).notify(MESSAGE);
        assertEquals(null, received.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void aRejectedCallNeverThrows() throws Exception {
        status = 500;
        assertDoesNotThrow(() -> notifier(Optional.of("T"), Optional.of("1")).notify(MESSAGE));
        assertNotNull(received.poll(5, TimeUnit.SECONDS));
    }

    @Test
    void anUnreachableServerNeverThrows() {
        TelegramContactNotifier unreachable =
                new TelegramContactNotifier(mapper, Optional.of("T"), Optional.of("1"), "http://127.0.0.1:1");
        assertDoesNotThrow(() -> unreachable.notify(MESSAGE));
    }

    @Test
    void aMalformedApiUrlNeverThrows() {
        TelegramContactNotifier broken =
                new TelegramContactNotifier(mapper, Optional.of("T"), Optional.of("1"), "http://[bad");
        assertDoesNotThrow(() -> broken.notify(MESSAGE));
    }
}
