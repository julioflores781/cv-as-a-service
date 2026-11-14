package dev.julioflores.cv.infrastructure.adapter.out.postgres;

import dev.julioflores.cv.application.port.out.AdminAccountRepository;
import dev.julioflores.cv.domain.AdminAccount;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;

/** La cuenta única en {@code admin_account}. Cada escritura es una sola sentencia, así que no hay carreras entre intentos. */
@ApplicationScoped
public class PostgresAdminAccountRepository implements AdminAccountRepository {

    private static final String SELECT = "SELECT password_hash, session_version, failed_attempts, locked_until, "
            + "password_changed_at, last_login_at FROM admin_account WHERE id = 1";
    private static final String FAILURE = "UPDATE admin_account SET failed_attempts = failed_attempts + 1, "
            + "locked_until = CASE WHEN failed_attempts + 1 >= ? THEN now() + (? * interval '1 second') "
            + "ELSE locked_until END WHERE id = 1";
    private static final String SUCCESS = "UPDATE admin_account SET failed_attempts = 0, locked_until = NULL, "
            + "last_login_at = now() WHERE id = 1";
    private static final String REPLACE_HASH = "UPDATE admin_account SET password_hash = ? "
            + "WHERE id = 1 AND password_hash = ?";
    private static final String CHANGE_PASSWORD = "UPDATE admin_account SET password_hash = ?, "
            + "session_version = session_version + 1, password_changed_at = now(), failed_attempts = 0, "
            + "locked_until = NULL WHERE id = 1 RETURNING session_version";
    private static final String UPSERT_PASSWORD = "INSERT INTO admin_account (id, password_hash) VALUES (1, ?) "
            + "ON CONFLICT (id) DO UPDATE SET password_hash = EXCLUDED.password_hash, "
            + "session_version = admin_account.session_version + 1, password_changed_at = now(), "
            + "failed_attempts = 0, locked_until = NULL RETURNING session_version";
    private static final String UNLOCK = "UPDATE admin_account SET failed_attempts = 0, locked_until = NULL WHERE id = 1";
    private static final String INVALIDATE = "UPDATE admin_account SET session_version = session_version + 1 "
            + "WHERE id = 1 RETURNING session_version";

    private final DataSource dataSource;

    public PostgresAdminAccountRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<AdminAccount> find() {
        try (Connection c = dataSource.getConnection();
                PreparedStatement s = c.prepareStatement(SELECT);
                ResultSet rs = s.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(new AdminAccount(
                    rs.getString(1), rs.getInt(2), rs.getInt(3),
                    instant(rs.getTimestamp(4)), instant(rs.getTimestamp(5)), instant(rs.getTimestamp(6))));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the admin account", e);
        }
    }

    @Override
    public void recordFailure(int maxFailures, Duration lock) {
        update(FAILURE, s -> {
            s.setInt(1, maxFailures);
            s.setLong(2, lock.toSeconds());
        });
    }

    @Override
    public void recordSuccess() {
        update(SUCCESS, s -> {});
    }

    @Override
    public void replaceHash(String oldHash, String newHash) {
        update(REPLACE_HASH, s -> {
            s.setString(1, newHash);
            s.setString(2, oldHash);
        });
    }

    @Override
    public int changePassword(String newHash) {
        return returningVersion(CHANGE_PASSWORD, s -> s.setString(1, newHash));
    }

    /**
     * Crea la cuenta o cambia su hash (alta y recuperación desde el comando del NAS). Si existe, sube la versión de
     * sesión y limpia intentos y bloqueo. Devuelve la versión de sesión resultante: 1 si la cuenta se acaba de crear.
     */
    public int upsertPassword(String newHash) {
        return returningVersion(UPSERT_PASSWORD, s -> s.setString(1, newHash));
    }

    /** Quita el bloqueo y los intentos fallidos sin tocar la contraseña ni las sesiones. {@code false} si no hay cuenta. */
    public boolean unlock() {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(UNLOCK)) {
            return s.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update the admin account", e);
        }
    }

    @Override
    public int invalidateSessions() {
        return returningVersion(INVALIDATE, s -> {});
    }

    private void update(String sql, Binder binder) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            binder.bind(s);
            s.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update the admin account", e);
        }
    }

    private int returningVersion(String sql, Binder binder) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            binder.bind(s);
            try (ResultSet rs = s.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("There is no admin account");
                }
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update the admin account", e);
        }
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
