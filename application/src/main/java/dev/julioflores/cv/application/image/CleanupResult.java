package dev.julioflores.cv.application.image;

import java.util.List;

/**
 * Resultado de la limpieza de huérfanas. En {@code dryRun}, {@code deleted} está vacío y {@code wouldDelete} lista lo que se
 * borraría; en una ejecución real es al revés. {@code kept} cuenta lo que no se toca (referenciadas o dentro de la gracia).
 */
public record CleanupResult(boolean dryRun, List<String> deleted, List<String> wouldDelete, int kept) {}
