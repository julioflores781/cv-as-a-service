package dev.julioflores.cv.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.julioflores.cv.infrastructure.config.ClientIp;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClientIpTest {

    @Test
    void acceptsIpv4AndIpv6Literals() { // T17
        assertEquals(Optional.of("203.0.113.9"), ClientIp.parse("203.0.113.9"));
        assertEquals(Optional.of("0.0.0.0"), ClientIp.parse("0.0.0.0"));
        assertEquals(Optional.of("255.255.255.255"), ClientIp.parse(" 255.255.255.255 "));
        assertEquals(Optional.of("2001:db8::1"), ClientIp.parse("2001:db8::1"));
        assertEquals(Optional.of("::1"), ClientIp.parse("::1"));
        assertEquals(Optional.of("::ffff:192.0.2.1"), ClientIp.parse("::ffff:192.0.2.1"));
        assertEquals(Optional.of("2001:0db8:85a3:0000:0000:8a2e:0370:7334"), ClientIp.parse("2001:0db8:85a3:0000:0000:8a2e:0370:7334"));
    }

    @Test
    void anIpv6ZoneIsDroppedAndAnInvalidZoneRejected() { // T17
        assertEquals(Optional.of("fe80::1"), ClientIp.parse("fe80::1%eth0"));
        assertEquals(Optional.empty(), ClientIp.parse("fe80::1%"));
        assertEquals(Optional.empty(), ClientIp.parse("fe80::1%eth0\nX: 1"));
        assertEquals(Optional.empty(), ClientIp.parse("fe80::1%" + "z".repeat(16)));
    }

    @Test
    void rejectsEverythingThatIsNotALiteral() { // T17
        assertEquals(Optional.empty(), ClientIp.parse(null));
        assertEquals(Optional.empty(), ClientIp.parse(""));
        assertEquals(Optional.empty(), ClientIp.parse("   "));
        assertEquals(Optional.empty(), ClientIp.parse("example.com"));
        assertEquals(Optional.empty(), ClientIp.parse("not-an-ip"));
        assertEquals(Optional.empty(), ClientIp.parse("256.1.1.1"));
        assertEquals(Optional.empty(), ClientIp.parse("1.2.3"));
        assertEquals(Optional.empty(), ClientIp.parse("01.2.3.4"));
        assertEquals(Optional.empty(), ClientIp.parse("1.2.3.4.5"));
        assertEquals(Optional.empty(), ClientIp.parse("203.0.113.9\nX-Forwarded: 1"));
        assertEquals(Optional.empty(), ClientIp.parse("203.0.113.9 ; DROP"));
        assertEquals(Optional.empty(), ClientIp.parse("2001:db8::zz"));
        assertEquals(Optional.empty(), ClientIp.parse(":::"));
        assertEquals(Optional.empty(), ClientIp.parse("1:2:3:4:5:6:7:8:9"));
        assertEquals(Optional.empty(), ClientIp.parse("2001:db8::" + "1".repeat(40))); // más de 45 caracteres
    }

    @Test
    void neverResolvesNames() { // T17: «localhost» o un host inexistente se descartan sin consultar DNS
        assertTimeoutPreemptively(Duration.ofMillis(500), () -> {
            assertEquals(Optional.empty(), ClientIp.parse("localhost"));
            assertEquals(Optional.empty(), ClientIp.parse("no-such-host.invalid"));
            assertEquals(Optional.empty(), ClientIp.parse("a:b.example.invalid"));
        });
    }

    @Test
    void firstValidSkipsInvalidCandidatesInOrder() { // extra fuera del plan: la regla CF-Connecting-IP > X-Client-IP
        assertEquals(Optional.of("203.0.113.10"), ClientIp.firstValid("not-an-ip", "203.0.113.10"));
        assertEquals(Optional.of("203.0.113.9"), ClientIp.firstValid("203.0.113.9", "203.0.113.10"));
        assertEquals(Optional.empty(), ClientIp.firstValid(null, "x"));
    }
}
