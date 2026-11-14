package dev.julioflores.cv.infrastructure.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Valida una IP de cliente recibida en una cabecera antes de escribirla en el log. Solo acepta literales (IPv4 o IPv6): un nombre de
 * host, un salto de línea o cualquier otro texto se descarta, y nunca se hace una resolución DNS.
 */
public final class ClientIp {

    /** Longitud máxima de una IPv6 textual (con IPv4 incrustada): 45. */
    private static final int MAX_LENGTH = 45;

    private static final Pattern IPV4 =
            Pattern.compile("(?:(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");
    private static final Pattern IPV6_CHARS = Pattern.compile("[0-9A-Fa-f:.]+");
    private static final Pattern ZONE = Pattern.compile("[A-Za-z0-9_.-]{1,15}");

    private ClientIp() {}

    /** La IP si es un literal válido (una IPv6 con zona {@code %eth0} se devuelve sin la zona); vacío si no. */
    public static Optional<String> parse(String raw) {
        if (raw == null || raw.length() > MAX_LENGTH + 16) {
            return Optional.empty();
        }
        String value = raw.strip();
        int zoneAt = value.indexOf('%');
        if (zoneAt >= 0) {
            if (!ZONE.matcher(value.substring(zoneAt + 1)).matches()) {
                return Optional.empty();
            }
            value = value.substring(0, zoneAt);
        }
        if (value.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        if (IPV4.matcher(value).matches()) {
            return Optional.of(value);
        }
        return isIpv6Literal(value) ? Optional.of(value) : Optional.empty();
    }

    /** La primera IP válida, en el orden dado; vacío si ninguna lo es. */
    public static Optional<String> firstValid(String... candidates) {
        for (String candidate : candidates) {
            Optional<String> ip = parse(candidate);
            if (ip.isPresent()) {
                return ip;
            }
        }
        return Optional.empty();
    }

    /** Con al menos un ':' y solo dígitos hexadecimales, ':' y '.', {@code InetAddress} trata el texto como literal: no consulta DNS. */
    private static boolean isIpv6Literal(String value) {
        if (value.indexOf(':') < 0 || !IPV6_CHARS.matcher(value).matches()) {
            return false;
        }
        try {
            return InetAddress.getByName(value) != null;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
