package dev.julioflores.cv.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.domain.ContactMessage;
import dev.julioflores.cv.infrastructure.LogRecords;
import dev.julioflores.cv.infrastructure.adapter.out.postgres.PostgresContactMessageRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import javax.sql.DataSource;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PostgresContactMessageRepositoryTest {

    private static final String LOGGER = PostgresContactMessageRepository.class.getName();

    @Inject
    PostgresContactMessageRepository repository;

    @Inject
    DataSource dataSource;

    private long idOf(String email) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement s = c.prepareStatement("SELECT id FROM contact_message WHERE email = ?")) {
            s.setString(1, email);
            try (ResultSet rs = s.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }

    // T64: el id que se loguea es el de la fila guardada
    @Test
    void theLoggedIdIsTheOneOfTheStoredRowAndNothingElseIsLogged() throws SQLException {
        String email = UUID.randomUUID() + "@example.com";
        String senderKey = "sender-" + UUID.randomUUID();
        try (LogRecords logs = LogRecords.attach()) {
            repository.save(new ContactMessage("Canary Nombre", email, "Canary mensaje de prueba"), senderKey);
            List<ExtLogRecord> lines = logs.byLogger(LOGGER);
            assertEquals(1, lines.size(), logs.dump());
            assertEquals(Level.INFO.intValue(), lines.get(0).getLevel().intValue());
            assertEquals("Contact message stored id=" + idOf(email), lines.get(0).getFormattedMessage());
            for (String leak : new String[] {"Canary Nombre", email, "Canary mensaje", senderKey}) {
                assertFalse(logs.dump().contains(leak), leak);
            }
        }
    }

    // Extra fuera del plan: dos guardados seguidos loguean ids distintos y crecientes (cada línea es de su fila)
    @Test
    void twoSavesLogTheirOwnDistinctIds() throws SQLException {
        String first = UUID.randomUUID() + "@example.com";
        String second = UUID.randomUUID() + "@example.com";
        try (LogRecords logs = LogRecords.attach()) {
            repository.save(new ContactMessage("Ada", first, "Mensaje número uno"), "k1");
            repository.save(new ContactMessage("Ada", second, "Mensaje número dos"), "k2");
            assertEquals(List.of("Contact message stored id=" + idOf(first), "Contact message stored id=" + idOf(second)),
                    LogRecords.messages(logs.byLogger(LOGGER)));
        }
    }

    // Extra fuera del plan: un fallo de BD se propaga (no se pierde en silencio) y no deja «stored»
    @Test
    void aFailedInsertStillThrowsAndLogsNoStoredLine() {
        try (LogRecords logs = LogRecords.attach()) {
            String tooLong = "x".repeat(300); // name es varchar(100): el INSERT falla
            assertThrows(IllegalStateException.class,
                    () -> repository.save(new ContactMessage(tooLong, "a@example.com", "Mensaje de prueba"), "k"));
            assertTrue(logs.byLogger(LOGGER).isEmpty(), logs.dump());
        }
    }
}
