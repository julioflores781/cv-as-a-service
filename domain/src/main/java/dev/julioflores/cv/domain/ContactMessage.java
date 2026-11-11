package dev.julioflores.cv.domain;

import java.util.Objects;

/** Un mensaje que un visitante deja a través de la web. Ya validado: lo que llega aquí es publicable y guardable. */
public record ContactMessage(String name, String email, String body) {

    public ContactMessage {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(body, "body");
    }
}
