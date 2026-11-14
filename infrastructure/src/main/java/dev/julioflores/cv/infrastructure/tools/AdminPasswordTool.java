package dev.julioflores.cv.infrastructure.tools;

import dev.julioflores.cv.application.port.out.PasswordHasher;
import dev.julioflores.cv.domain.AdminAccount;
import dev.julioflores.cv.domain.AdminPasswordPolicy;
import dev.julioflores.cv.infrastructure.adapter.out.postgres.PostgresAdminAccountRepository;
import dev.julioflores.cv.infrastructure.adapter.out.security.Argon2PasswordHasher;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Herramienta de línea de comandos del operador (se ejecuta con {@code scripts/admin-password.sh} dentro del contenedor
 * de la API, sin arrancar Quarkus). Crea o cambia la contraseña del administrador, quita el bloqueo por intentos
 * fallidos y muestra el estado de la cuenta.
 *
 * <p>Usa el mismo {@link Argon2PasswordHasher}, la misma {@link AdminPasswordPolicy} y el mismo repositorio que la API.
 * Ningún error imprime trazas ni el texto de excepciones del driver (podrían llevar credenciales de la BD).
 * La contraseña solo se lee del terminal, sin eco y con confirmación: nunca es un argumento. El hash no se muestra.
 */
public final class AdminPasswordTool {

    static final int OK = 0;
    static final int FAILED = 1;
    static final int USAGE = 2;

    /** Fallo no previsto: mensaje fijo, sin la excepción (podría llevar la URL, el usuario o la contraseña de la BD). */
    static final String UNEXPECTED = "Error: unexpected failure (details withheld: they could contain database credentials).";

    static final String USAGE_TEXT = """
            Usage: AdminPasswordTool <command>

              set-password  create the admin account or change its password (asks twice, no echo);
                            invalidates every open session and clears the lock
              unlock        clear the lock and the failed attempts, keeping the password
              status        show whether the account exists, its lock and when the password changed
            """;

    /** Lee una contraseña del terminal sin eco; {@code null} si la entrada se cerró. */
    @FunctionalInterface
    public interface PasswordReader {
        char[] readPassword(String prompt);
    }

    enum Command {
        SET_PASSWORD, UNLOCK, STATUS;

        static Optional<Command> parse(String[] args) {
            if (args.length != 1) {
                return Optional.empty();
            }
            return switch (args[0]) {
                case "set-password" -> Optional.of(SET_PASSWORD);
                case "unlock" -> Optional.of(UNLOCK);
                case "status" -> Optional.of(STATUS);
                default -> Optional.empty();
            };
        }
    }

    private final PasswordHasher hasher;
    private final PostgresAdminAccountRepository repository;
    private final PasswordReader reader;
    private final PrintStream out;
    private final PrintStream err;

    /** @param reader {@code null} si no hay terminal interactivo. */
    AdminPasswordTool(PasswordHasher hasher, PostgresAdminAccountRepository repository, PasswordReader reader,
            PrintStream out, PrintStream err) {
        this.hasher = hasher;
        this.repository = repository;
        this.reader = reader;
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) {
        Optional<Command> command = Command.parse(args);
        if (command.isEmpty()) {
            System.err.print(USAGE_TEXT);
            System.exit(USAGE);
            return;
        }
        java.io.Console console = System.console();
        PasswordReader reader = console == null ? null : prompt -> console.readPassword("%s", prompt);
        int code;
        try {
            code = fromEnvironment(System.getenv(), reader, System.out, System.err).run(command.get());
        } catch (IllegalStateException e) {
            System.err.println("Error: " + e.getMessage());
            code = FAILED;
        } catch (RuntimeException e) {
            System.err.println(UNEXPECTED);
            code = FAILED;
        }
        System.exit(code);
    }

    /**
     * Cablea la herramienta como la API: parámetros de Argon2 de {@code application.properties} (con el mismo
     * override por variable de entorno) y la base de datos de {@code QUARKUS_DATASOURCE_*}.
     */
    static AdminPasswordTool fromEnvironment(Map<String, String> env, PasswordReader reader, PrintStream out,
            PrintStream err) {
        String url = env.get("QUARKUS_DATASOURCE_JDBC_URL");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("QUARKUS_DATASOURCE_JDBC_URL is not set: run it inside the API container");
        }
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        try {
            dataSource.setUrl(url);
        } catch (RuntimeException e) {
            // Sin eco del valor ni de la causa: la URL puede llevar usuario y contraseña.
            throw new IllegalStateException("QUARKUS_DATASOURCE_JDBC_URL is not a valid PostgreSQL JDBC URL");
        }
        dataSource.setUser(env.get("QUARKUS_DATASOURCE_USERNAME"));
        dataSource.setPassword(env.get("QUARKUS_DATASOURCE_PASSWORD"));
        Properties defaults = applicationProperties();
        PasswordHasher hasher = new Argon2PasswordHasher(
                intSetting(env, defaults, "cv.admin.argon2.memory-kib"),
                intSetting(env, defaults, "cv.admin.argon2.iterations"),
                intSetting(env, defaults, "cv.admin.argon2.parallelism"));
        return new AdminPasswordTool(hasher, new PostgresAdminAccountRepository(dataSource), reader, out, err);
    }

    /** Todos los {@code application.properties} del classpath: el primero que define una clave gana. */
    private static Properties applicationProperties() {
        Properties merged = new Properties();
        try {
            var resources = AdminPasswordTool.class.getClassLoader().getResources("application.properties");
            while (resources.hasMoreElements()) {
                Properties found = new Properties();
                try (InputStream in = resources.nextElement().openStream()) {
                    found.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                }
                found.forEach(merged::putIfAbsent);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read application.properties", e);
        }
        return merged;
    }

    /** El mismo valor que ve la API: la variable de entorno equivalente si existe, si no el de application.properties. */
    private static int intSetting(Map<String, String> env, Properties defaults, String key) {
        String value = env.get(key.toUpperCase().replaceAll("[^A-Z0-9]", "_"));
        if (value == null || value.isBlank()) {
            value = defaults.getProperty(key);
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException e) {
            throw new IllegalStateException("Invalid or missing setting " + key, e);
        }
    }

    int run(String... args) {
        Optional<Command> command = Command.parse(args);
        if (command.isEmpty()) {
            err.print(USAGE_TEXT);
            return USAGE;
        }
        return run(command.get());
    }

    private int run(Command command) {
        try {
            return switch (command) {
                case SET_PASSWORD -> setPassword();
                case UNLOCK -> unlock();
                case STATUS -> status();
            };
        } catch (IllegalStateException e) {
            err.println("Error: " + e.getMessage());
            return FAILED;
        } catch (RuntimeException e) {
            err.println(UNEXPECTED);
            return FAILED;
        }
    }

    private int setPassword() {
        if (reader == null) {
            err.println("Error: a terminal is required to type the password (use docker exec -it).");
            return FAILED;
        }
        String password = read("New password: ");
        String confirmation = password == null ? null : read("Repeat the password: ");
        if (password == null || confirmation == null) {
            err.println("Error: no password was entered.");
            return FAILED;
        }
        if (!password.equals(confirmation)) {
            err.println("Error: the passwords do not match. Nothing was changed.");
            return FAILED;
        }
        if (!AdminPasswordPolicy.isAcceptable(password, null)) {
            err.printf("Error: the password must have between %d and %d characters. Nothing was changed.%n",
                    AdminPasswordPolicy.MIN_LENGTH, AdminPasswordPolicy.MAX_LENGTH);
            return FAILED;
        }
        int version = repository.upsertPassword(hasher.hash(password));
        out.println(version == 1
                ? "Admin account created."
                : "Password changed: every open session was invalidated and the lock was cleared.");
        return OK;
    }

    private String read(String prompt) {
        char[] chars = reader.readPassword(prompt);
        if (chars == null) {
            return null;
        }
        String value = new String(chars);
        Arrays.fill(chars, '\0');
        return value;
    }

    private int unlock() {
        if (repository.unlock()) {
            out.println("Lock and failed attempts cleared.");
            return OK;
        }
        err.println("Error: there is no admin account. Create it with set-password.");
        return FAILED;
    }

    private int status() {
        Optional<AdminAccount> found = repository.find();
        if (found.isEmpty()) {
            out.println("Account: none (create it with set-password)");
            return OK;
        }
        AdminAccount account = found.get();
        out.println("Account: exists");
        out.println(account.isLockedAt(Instant.now())
                ? "Locked: yes, until " + account.lockedUntil()
                : "Locked: no");
        out.println("Failed attempts: " + account.failedAttempts());
        out.println("Session version: " + account.sessionVersion());
        out.println("Password changed at: " + account.passwordChangedAt());
        out.println("Last login at: " + (account.lastLoginAt() == null ? "never" : account.lastLoginAt()));
        return OK;
    }
}
