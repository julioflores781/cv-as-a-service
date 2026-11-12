package dev.julioflores.cv.application.contact;

import java.time.Instant;

/**
 * Un mensaje de contacto ya guardado, tal como lo ve el administrador. No lleva el hash de la IP del remitente a propósito:
 * ese dato no sale de la base de datos.
 */
public record ContactMessageEntry(long id, String name, String email, String body, Instant createdAt) {}
