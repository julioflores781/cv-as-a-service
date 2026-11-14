package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.application.content.ContentVersion;

public interface GetContentVersionUseCase {

    /** Una instantánea concreta; {@code ContentVersionNotFoundException} si no existe. */
    ContentVersion get(String kind, String language, int version);
}
