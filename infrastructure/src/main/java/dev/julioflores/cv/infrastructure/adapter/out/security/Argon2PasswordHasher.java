package dev.julioflores.cv.infrastructure.adapter.out.security;

import dev.julioflores.cv.application.port.out.PasswordHasher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Argon2id (BouncyCastle, Java puro: sin binarios nativos, así vale para amd64 y arm64). El hash va en formato PHC
 * {@code $argon2id$v=19$m=…,t=…,p=…$sal$hash} con los parámetros dentro, de modo que subir el coste no rompe los
 * hashes antiguos y {@link #needsRehash} los detecta. Como mucho dos hashes a la vez, para acotar la memoria.
 */
@ApplicationScoped
public class Argon2PasswordHasher implements PasswordHasher {

    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final int MAX_CONCURRENT = 2;
    /** Tope de lo que se acepta leer de un hash guardado, para que uno manipulado no agote la memoria. */
    private static final int MAX_MEMORY_KIB = 1 << 20;
    private static final int MAX_ITERATIONS = 64;
    private static final int MAX_PARALLELISM = 16;
    private static final Pattern PHC = Pattern.compile(
            "^\\$argon2id\\$v=19\\$m=(\\d+),t=(\\d+),p=(\\d+)\\$([A-Za-z0-9+/]+)\\$([A-Za-z0-9+/]+)$");

    private final int memoryKib;
    private final int iterations;
    private final int parallelism;
    private final SecureRandom random = new SecureRandom();
    private final Semaphore slots = new Semaphore(MAX_CONCURRENT);

    /** Público para que la herramienta de línea de comandos (sin CDI) use exactamente el mismo hasher. */
    @Inject
    public Argon2PasswordHasher(
            @ConfigProperty(name = "cv.admin.argon2.memory-kib") int memoryKib,
            @ConfigProperty(name = "cv.admin.argon2.iterations") int iterations,
            @ConfigProperty(name = "cv.admin.argon2.parallelism") int parallelism) {
        this.memoryKib = memoryKib;
        this.iterations = iterations;
        this.parallelism = parallelism;
    }

    @Override
    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt, memoryKib, iterations, parallelism);
        return "$argon2id$v=19$m=%d,t=%d,p=%d$%s$%s".formatted(
                memoryKib, iterations, parallelism, encode(salt), encode(hash));
    }

    @Override
    public boolean verify(String password, String hash) {
        Matcher m = parse(hash);
        if (m == null) {
            return false;
        }
        try {
            byte[] expected = Base64.getDecoder().decode(pad(m.group(5)));
            byte[] actual = derive(password, Base64.getDecoder().decode(pad(m.group(4))),
                    Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
                    expected.length);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean needsRehash(String hash) {
        Matcher m = parse(hash);
        return m != null
                && (Integer.parseInt(m.group(1)) != memoryKib
                        || Integer.parseInt(m.group(2)) != iterations
                        || Integer.parseInt(m.group(3)) != parallelism);
    }

    /** Devuelve el patrón ya emparejado si el hash es un PHC argon2id con parámetros razonables; si no, {@code null}. */
    private static Matcher parse(String hash) {
        if (hash == null) {
            return null;
        }
        Matcher m = PHC.matcher(hash);
        if (!m.matches()) {
            return null;
        }
        try {
            long memory = Long.parseLong(m.group(1));
            long time = Long.parseLong(m.group(2));
            long lanes = Long.parseLong(m.group(3));
            boolean sane = memory >= 8 && memory <= MAX_MEMORY_KIB && time >= 1 && time <= MAX_ITERATIONS
                    && lanes >= 1 && lanes <= MAX_PARALLELISM;
            return sane ? m : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private byte[] derive(String password, byte[] salt, int memory, int time, int lanes) {
        return derive(password, salt, memory, time, lanes, HASH_BYTES);
    }

    private byte[] derive(String password, byte[] salt, int memory, int time, int lanes, int length) {
        Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(memory)
                .withIterations(time)
                .withParallelism(lanes)
                .withSalt(salt)
                .build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(parameters);
        byte[] out = new byte[length];
        slots.acquireUninterruptibly();
        try {
            generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), out);
        } finally {
            slots.release();
        }
        return out;
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String pad(String unpadded) {
        return unpadded + "=".repeat((4 - unpadded.length() % 4) % 4);
    }
}
