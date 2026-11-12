package dev.julioflores.cv.infrastructure.adapter.out.postgres;

import dev.julioflores.cv.application.contact.ContactMessageEntry;
import dev.julioflores.cv.application.port.out.ContactMessageRepository;
import dev.julioflores.cv.domain.ContactMessage;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/** Guarda los mensajes de contacto en la tabla {@code contact_message}. Un fallo se propaga: no se pierde un mensaje en silencio. */
@ApplicationScoped
public class PostgresContactMessageRepository implements ContactMessageRepository {

    private static final String INSERT = "INSERT INTO contact_message (name, email, body, ip_hash) VALUES (?, ?, ?, ?)";

    // Nunca se selecciona ip_hash: no hay forma de que llegue hasta la API.
    private static final String COLUMNS = "SELECT id, name, email, body, created_at FROM contact_message";
    private static final String ORDER = " ORDER BY created_at DESC, id DESC LIMIT ?";
    private static final String NEWEST = COLUMNS + ORDER;
    // Un cursor que no existe da una subconsulta vacía (NULL): la comparación no se cumple y la lista sale vacía.
    private static final String BEFORE = COLUMNS
            + " WHERE (created_at, id) < (SELECT created_at, id FROM contact_message WHERE id = ?)" + ORDER;

    private final DataSource dataSource;

    public PostgresContactMessageRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(ContactMessage message, String senderKey) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, message.name());
            statement.setString(2, message.email());
            statement.setString(3, message.body());
            statement.setString(4, senderKey);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save the contact message", e);
        }
    }

    @Override
    public List<ContactMessageEntry> findNewestFirst(Long before, int limit) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(before == null ? NEWEST : BEFORE)) {
            int next = 1;
            if (before != null) {
                statement.setLong(next++, before);
            }
            statement.setInt(next, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<ContactMessageEntry> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(new ContactMessageEntry(rows.getLong("id"), rows.getString("name"), rows.getString("email"),
                            rows.getString("body"), rows.getObject("created_at", OffsetDateTime.class).toInstant()));
                }
                return found;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the contact messages", e);
        }
    }
}
