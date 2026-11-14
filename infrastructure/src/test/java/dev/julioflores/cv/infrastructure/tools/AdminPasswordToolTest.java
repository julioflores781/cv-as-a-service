package dev.julioflores.cv.infrastructure.tools;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.port.out.PasswordHasher;
import dev.julioflores.cv.domain.AdminAccount;
import dev.julioflores.cv.infrastructure.AdminTestSupport;
import dev.julioflores.cv.infrastructure.adapter.out.postgres.PostgresAdminAccountRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AdminPasswordToolTest {

    private static final String LOGIN = "/api/v1/admin/auth/login";
    private static final String NEW_PASSWORD = "contraseña del comando 1";

    @Inject
    PasswordHasher hasher;

    @Inject
    PostgresAdminAccountRepository repository;

    @Inject
    AdminTestSupport support;

    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    private final List<String> prompts = new ArrayList<>();

    @BeforeEach
    void emptyTable() {
        support.clear();
    }

    private AdminPasswordTool tool(String... typed) {
        Deque<String> input = new ArrayDeque<>(List.of(typed));
        return new AdminPasswordTool(hasher, repository, prompt -> {
            prompts.add(prompt);
            String next = input.poll();
            return next == null ? null : next.toCharArray();
        }, new PrintStream(stdout, true, StandardCharsets.UTF_8), new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    private String out() {
        return stdout.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return stderr.toString(StandardCharsets.UTF_8);
    }

    private static int loginStatus(String password) {
        return given().contentType(ContentType.JSON).body("{\"password\":\"" + password + "\"}")
                .when().post(LOGIN).then().extract().statusCode();
    }

    @Test
    void setPasswordWithoutAnAccountCreatesItAndTheLoginWorks() { // T91
        assertEquals(0, tool(NEW_PASSWORD, NEW_PASSWORD).run("set-password"));

        AdminAccount account = repository.find().orElseThrow();
        assertTrue(account.passwordHash().startsWith("$argon2id$"));
        assertTrue(hasher.verify(NEW_PASSWORD, account.passwordHash()));
        assertEquals(1, account.sessionVersion());
        assertEquals(200, loginStatus(NEW_PASSWORD));
        assertTrue(out().contains("created"));
        assertFalse((out() + err()).contains(account.passwordHash()), "el hash no se muestra");
        assertFalse((out() + err()).contains(NEW_PASSWORD), "la contraseña no se muestra");
        assertEquals(List.of("New password: ", "Repeat the password: "), prompts);
    }

    @Test
    void setPasswordOnAnExistingAccountChangesTheHashBumpsTheVersionAndClearsTheLock() { // T92
        support.resetAccount();
        support.execute("UPDATE admin_account SET failed_attempts = 7, locked_until = now() + interval '1 hour'");
        String oldHash = repository.find().orElseThrow().passwordHash();
        String oldToken = support.token(1);

        assertEquals(0, tool(NEW_PASSWORD, NEW_PASSWORD).run("set-password"));

        AdminAccount account = repository.find().orElseThrow();
        assertNotEquals(oldHash, account.passwordHash());
        assertTrue(hasher.verify(NEW_PASSWORD, account.passwordHash()));
        assertEquals(2, account.sessionVersion());
        assertEquals(0, account.failedAttempts());
        assertNull(account.lockedUntil());
        assertTrue(out().contains("invalidated"));
        assertEquals(200, loginStatus(NEW_PASSWORD));
        assertEquals(401, given().header("Authorization", "Bearer " + oldToken)
                .when().get("/api/v1/admin/auth/session").then().extract().statusCode(),
                "el token anterior ya no vale");
    }

    @Test
    void aWeakPasswordOrADifferentConfirmationFailsWithoutTouchingTheDatabase() { // T93
        support.resetAccount();
        AdminAccount before = repository.find().orElseThrow();

        assertEquals(1, tool("corta", "corta").run("set-password"));
        assertTrue(err().contains("between 12 and 128"));
        assertEquals(1, tool(NEW_PASSWORD, NEW_PASSWORD + "x").run("set-password"));
        assertTrue(err().contains("do not match"));
        assertEquals(1, tool("   ".repeat(6), "   ".repeat(6)).run("set-password"));

        assertEquals(before, repository.find().orElseThrow());

        support.clear();
        assertEquals(1, tool("corta", "corta").run("set-password"));
        assertTrue(repository.find().isEmpty(), "sin cuenta tampoco se crea una con una contraseña débil");
    }

    @Test
    void unlockClearsTheLockAndStatusReportsWithoutShowingTheHash() { // T94
        support.resetAccount();
        support.execute("UPDATE admin_account SET failed_attempts = 5, locked_until = now() + interval '15 minutes'");
        String hash = repository.find().orElseThrow().passwordHash();

        assertEquals(0, tool().run("status"));
        String locked = out();
        assertTrue(locked.contains("Account: exists"));
        assertTrue(locked.contains("Locked: yes, until"));
        assertTrue(locked.contains("Failed attempts: 5"));
        assertTrue(locked.contains("Session version: 1"));
        assertFalse(locked.contains(hash));
        assertFalse(locked.contains("argon2"));

        assertEquals(0, tool().run("unlock"));
        AdminAccount account = repository.find().orElseThrow();
        assertEquals(0, account.failedAttempts());
        assertFalse(account.isLockedAt(Instant.now()));
        assertEquals(hash, account.passwordHash(), "unlock no toca la contraseña");
        assertEquals(1, account.sessionVersion(), "ni las sesiones");
        assertEquals(200, loginStatus(AdminTestSupport.PASSWORD));

        stdout.reset();
        assertEquals(0, tool().run("status"));
        assertTrue(out().contains("Locked: no"));
        assertTrue(out().contains("Last login at: "));
    }

    @Test
    void unknownOrMissingArgumentsPrintTheUsageAndExitNonZero() { // T95
        for (String[] args : new String[][] {{}, {"nope"}, {"status", "extra"}, {"SET-PASSWORD"}, {"--password=abc"}}) {
            stderr.reset();
            assertEquals(2, tool().run(args), String.join(" ", args));
            assertTrue(err().contains("Usage: AdminPasswordTool"));
            assertTrue(err().contains("set-password") && err().contains("unlock") && err().contains("status"));
        }
        assertTrue(repository.find().isEmpty());
    }

    // --- extras fuera del plan ---

    @Test
    void statusWithoutAnAccountSaysSoAndUnlockFails() {
        assertEquals(0, tool().run("status"));
        assertTrue(out().contains("Account: none"));
        assertEquals(1, tool().run("unlock"));
        assertTrue(err().contains("no admin account"));
    }

    @Test
    void withoutATerminalSetPasswordRefusesAndTheInputEndingEarlyIsHandled() {
        AdminPasswordTool noTerminal = new AdminPasswordTool(hasher, repository, null,
                new PrintStream(stdout), new PrintStream(stderr));
        assertEquals(1, noTerminal.run("set-password"));
        assertTrue(err().contains("terminal is required"));

        stderr.reset();
        assertEquals(1, tool().run("set-password"), "entrada cerrada de entrada");
        assertEquals(1, tool(NEW_PASSWORD).run("set-password"), "entrada cerrada tras la primera contraseña");
        assertTrue(err().contains("no password was entered"));
        assertTrue(repository.find().isEmpty());
    }

    @Test
    void aDatabaseFailureIsReportedWithoutAStackTrace() {
        support.execute("ALTER TABLE admin_account RENAME TO admin_account_off");
        try {
            assertEquals(1, tool().run("status"));
            assertTrue(err().startsWith("Error: "));
        } finally {
            support.execute("ALTER TABLE admin_account_off RENAME TO admin_account");
        }
    }

    @Test
    void theEnvironmentWiringBuildsAToolThatWorksAndAReadsTheSameArgon2Defaults() {
        var config = ConfigProvider.getConfig();
        Map<String, String> env = Map.of(
                "QUARKUS_DATASOURCE_JDBC_URL", config.getValue("quarkus.datasource.jdbc.url", String.class),
                "QUARKUS_DATASOURCE_USERNAME", config.getValue("quarkus.datasource.username", String.class),
                "QUARKUS_DATASOURCE_PASSWORD", config.getValue("quarkus.datasource.password", String.class),
                "CV_ADMIN_ARGON2_MEMORY_KIB", "1024", "CV_ADMIN_ARGON2_ITERATIONS", "1");
        var wired = AdminPasswordTool.fromEnvironment(env,
                prompt -> NEW_PASSWORD.toCharArray(), new PrintStream(stdout), new PrintStream(stderr));
        assertEquals(0, wired.run("set-password"));
        String hash = repository.find().orElseThrow().passwordHash();
        assertTrue(hash.startsWith("$argon2id$v=19$m=1024,t=1,p=1$"), "env override + p del application.properties: " + hash);
        assertEquals(200, loginStatus(NEW_PASSWORD));

        var defaults = AdminPasswordTool.fromEnvironment(
                Map.of("QUARKUS_DATASOURCE_JDBC_URL", env.get("QUARKUS_DATASOURCE_JDBC_URL"),
                        "QUARKUS_DATASOURCE_USERNAME", env.get("QUARKUS_DATASOURCE_USERNAME"),
                        "QUARKUS_DATASOURCE_PASSWORD", env.get("QUARKUS_DATASOURCE_PASSWORD")),
                prompt -> NEW_PASSWORD.toCharArray(), new PrintStream(stdout), new PrintStream(stderr));
        assertEquals(0, defaults.run("set-password"));
        assertTrue(repository.find().orElseThrow().passwordHash().startsWith("$argon2id$v=19$m=65536,t=3,p=1$"));
    }

    @Test
    void theEnvironmentWiringRejectsAMissingDatabaseOrAnInvalidSetting() {
        PrintStream sink = new PrintStream(stdout);
        assertThrows(IllegalStateException.class, () -> AdminPasswordTool.fromEnvironment(Map.of(), null, sink, sink));
        assertThrows(IllegalStateException.class, () -> AdminPasswordTool.fromEnvironment(
                Map.of("QUARKUS_DATASOURCE_JDBC_URL", "jdbc:postgresql://x/y", "CV_ADMIN_ARGON2_ITERATIONS", "abc"),
                null, sink, sink));
    }

    private static final String SECRET = "SECRETXYZ";

    private void assertNoSecretsAnywhere() {
        String all = out() + err();
        assertFalse(all.contains(SECRET), all);
        assertFalse(all.contains("jdbc:"), all);
        assertFalse(all.contains("Exception"), all);
        assertFalse(all.contains("\tat "), all);
    }

    @Test
    void anInvalidJdbcUrlIsReportedWithoutEchoingItsCredentials() {
        for (String url : new String[] {"jdbc:postgresql://x:5432/cv?user=postgres&password=" + SECRET + "&bad=%zz",
                "mysql://u:" + SECRET + "@x/y", "jdbc:postgresql:///?password=" + SECRET + "&x=%"}) {
            var e = assertThrows(IllegalStateException.class, () -> AdminPasswordTool.fromEnvironment(
                    Map.of("QUARKUS_DATASOURCE_JDBC_URL", url), null, new PrintStream(stdout), new PrintStream(stderr)));
            assertFalse(String.valueOf(e.getMessage()).contains(SECRET));
            assertNull(e.getCause(), "sin causa que arrastre la URL");
        }
    }

    @Test
    void anUnreachableDatabaseAndWrongCredentialsNeverPrintTheSecrets() {
        var config = ConfigProvider.getConfig();
        String realUrl = config.getValue("quarkus.datasource.jdbc.url", String.class);
        String user = config.getValue("quarkus.datasource.username", String.class);
        Map<String, String> unreachable = Map.of(
                "QUARKUS_DATASOURCE_JDBC_URL", "jdbc:postgresql://127.0.0.1:1/cv?password=" + SECRET + "&connectTimeout=1",
                "QUARKUS_DATASOURCE_USERNAME", user, "QUARKUS_DATASOURCE_PASSWORD", SECRET);
        Map<String, String> wrongPassword = Map.of(
                "QUARKUS_DATASOURCE_JDBC_URL", realUrl,
                "QUARKUS_DATASOURCE_USERNAME", user, "QUARKUS_DATASOURCE_PASSWORD", SECRET);
        for (Map<String, String> env : List.of(unreachable, wrongPassword)) {
            for (String command : new String[] {"status", "unlock", "set-password"}) {
                stdout.reset();
                stderr.reset();
                var wired = AdminPasswordTool.fromEnvironment(env,
                        prompt -> NEW_PASSWORD.toCharArray(), new PrintStream(stdout), new PrintStream(stderr));
                assertEquals(1, wired.run(command), command);
                assertTrue(err().startsWith("Error: "), err());
                assertNoSecretsAnywhere();
            }
        }
    }

    @Test
    void anUnexpectedExceptionPrintsAFixedMessageWithoutItsText() {
        PasswordHasher exploding = new PasswordHasher() {
            public String hash(String password) {
                throw new IllegalArgumentException("jdbc:postgresql://x?password=" + SECRET);
            }

            public boolean verify(String password, String hash) {
                return false;
            }

            public boolean needsRehash(String hash) {
                return false;
            }
        };
        var broken = new AdminPasswordTool(exploding, repository, prompt -> NEW_PASSWORD.toCharArray(),
                new PrintStream(stdout), new PrintStream(stderr));
        assertEquals(1, broken.run("set-password"));
        assertEquals(AdminPasswordTool.UNEXPECTED + System.lineSeparator(), err());
        assertNoSecretsAnywhere();
    }
}
