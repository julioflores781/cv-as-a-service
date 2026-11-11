package dev.julioflores.cv.application.contact;

/**
 * Lo que llega del formulario, sin validar.
 *
 * @param website campo trampa: las personas no lo ven y no lo rellenan, los bots sí
 * @param senderKey identificador opaco de quien envía (hash de su IP); nunca la IP en claro
 */
public record ContactSubmission(String name, String email, String message, String website, String senderKey) {}
