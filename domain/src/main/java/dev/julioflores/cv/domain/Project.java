package dev.julioflores.cv.domain;

import java.util.List;
import java.util.Objects;

/**
 * Un proyecto en el que Julio trabajó. {@code clientLabel} describe al cliente sin nombrarlo («prepaga argentina»).
 * El nombre real ({@code client}) solo se conserva si el proyecto es {@link Disclosure#PUBLIC}: en uno anónimo se descarta
 * aquí mismo, para que ninguna capa superior pueda filtrarlo por error. Con las imágenes pasa lo mismo: solo un proyecto propio
 * ({@code own}) y público conserva las suyas; una captura de un proyecto de empleador o anónimo podría revelar al cliente.
 */
public record Project(
        String id,
        String title,
        String summary,
        ProjectRelation relation,
        String role,
        Disclosure disclosure,
        String client,
        String clientLabel,
        Period period,
        List<String> stack,
        List<String> highlights,
        List<ProjectLink> links,
        List<ProjectImage> images) {

    public Project {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(relation, "relation");
        Objects.requireNonNull(disclosure, "disclosure");
        client = disclosure == Disclosure.PUBLIC ? client : null;
        stack = Lists.copy(stack);
        highlights = Lists.copy(highlights);
        links = Lists.copy(links);
        images = relation == ProjectRelation.OWN && disclosure == Disclosure.PUBLIC ? Lists.copy(images) : List.of();
    }

    /** Un proyecto sin imágenes. */
    public Project(
            String id,
            String title,
            String summary,
            ProjectRelation relation,
            String role,
            Disclosure disclosure,
            String client,
            String clientLabel,
            Period period,
            List<String> stack,
            List<String> highlights,
            List<ProjectLink> links) {
        this(id, title, summary, relation, role, disclosure, client, clientLabel, period, stack, highlights, links, List.of());
    }
}
