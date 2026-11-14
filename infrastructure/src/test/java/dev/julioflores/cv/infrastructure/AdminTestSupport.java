package dev.julioflores.cv.infrastructure;

import dev.julioflores.cv.application.admin.AdminSession;
import dev.julioflores.cv.application.port.out.PasswordHasher;
import dev.julioflores.cv.application.port.out.SessionTokenIssuer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;

/** Apoyo de las pruebas de administración: crea la cuenta por SQL directo (el comando del NAS llega en otro slug). */
@ApplicationScoped
public class AdminTestSupport {

    public static final String PASSWORD = "una frase larga y secreta";

    @Inject
    DataSource dataSource;

    @Inject
    PasswordHasher hasher;

    @Inject
    SessionTokenIssuer issuer;

    /** Deja la tabla con una sola cuenta: contraseña {@link #PASSWORD}, versión 1, sin fallos. */
    public void resetAccount() {
        resetAccount(PASSWORD, 1);
    }

    public void resetAccount(String password, int sessionVersion) {
        clear();
        execute("INSERT INTO admin_account (id, password_hash, session_version) VALUES (1, ?, ?)",
                hasher.hash(password), sessionVersion);
    }

    public void clear() {
        execute("DELETE FROM admin_account");
    }

    /** Token válido para la versión de sesión dada. */
    public String token(int sessionVersion) {
        AdminSession session = issuer.issue(sessionVersion);
        return session.accessToken();
    }

    public void execute(String sql, Object... params) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                s.setObject(i + 1, params[i]);
            }
            s.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public String queryString(String sql) {
        try (Connection c = dataSource.getConnection();
                PreparedStatement s = c.prepareStatement(sql);
                var rs = s.executeQuery()) {
            rs.next();
            return rs.getString(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public int queryInt(String sql) {
        try (Connection c = dataSource.getConnection();
                PreparedStatement s = c.prepareStatement(sql);
                var rs = s.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
