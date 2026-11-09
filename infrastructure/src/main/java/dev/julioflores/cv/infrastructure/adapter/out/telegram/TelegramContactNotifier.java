package dev.julioflores.cv.infrastructure.adapter.out.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.julioflores.cv.application.port.out.ContactNotifier;
import dev.julioflores.cv.domain.ContactMessage;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Avisa por Telegram (Bot API, {@code sendMessage}) de cada mensaje nuevo. Sin token o sin chat configurados no hace nada.
 * El envío es asíncrono y nunca falla hacia fuera: el mensaje ya está guardado. En los logs no van ni el mensaje ni el
 * token (la URL lo lleva), solo el tipo de error o el estado HTTP.
 */
@ApplicationScoped
public class TelegramContactNotifier implements ContactNotifier {

    private static final Logger LOG = Logger.getLogger(TelegramContactNotifier.class);
    /** Telegram admite hasta 4096 caracteres por mensaje. */
    private static final int MAX_TEXT = 4000;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper;
    private final Optional<String> botToken;
    private final Optional<String> chatId;
    private final String apiUrl;

    public TelegramContactNotifier(
            ObjectMapper mapper,
            @ConfigProperty(name = "cv.contact.telegram.bot-token") Optional<String> botToken,
            @ConfigProperty(name = "cv.contact.telegram.chat-id") Optional<String> chatId,
            @ConfigProperty(name = "cv.contact.telegram.api-url", defaultValue = "https://api.telegram.org") String apiUrl) {
        this.mapper = mapper;
        this.botToken = botToken.filter(value -> !value.isBlank());
        this.chatId = chatId.filter(value -> !value.isBlank());
        this.apiUrl = apiUrl;
    }

    @Override
    public void notify(ContactMessage message) {
        if (botToken.isEmpty() || chatId.isEmpty()) {
            LOG.debug("Telegram notifications are not configured; skipping");
            return;
        }
        try {
            String payload = mapper.writeValueAsString(Map.of("chat_id", chatId.get(), "text", text(message)));
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl + "/bot" + botToken.get() + "/sendMessage"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            http.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
                if (error != null) {
                    LOG.warnf("Telegram notification failed: %s", error.getClass().getSimpleName());
                } else if (response.statusCode() / 100 != 2) {
                    LOG.warnf("Telegram notification rejected with HTTP %d", response.statusCode());
                }
            });
        } catch (Exception e) {
            LOG.warnf("Telegram notification could not be sent: %s", e.getClass().getSimpleName());
        }
    }

    private static String text(ContactMessage message) {
        String text = "Nuevo mensaje en el CV\nDe: " + message.name() + " <" + message.email() + ">\n\n" + message.body();
        return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
    }
}
