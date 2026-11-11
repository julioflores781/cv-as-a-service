package dev.julioflores.cv.infrastructure.adapter.out.postgres;

import dev.julioflores.cv.application.port.out.ContactMessageRepository;
import dev.julioflores.cv.domain.ContactMessage;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;

/** Guarda los mensajes de contacto en la tabla {@code contact_message}. Un fallo se propaga: no se pierde un mensaje en silencio. */
@ApplicationScoped
public class PostgresContactMessageRepository implements ContactMessageRepository {

    private static final String INSERT = "INSERT INTO contact_message (name, email, body, ip_hash) VALUES (?, ?, ?, ?)";

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
}
