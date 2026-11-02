package dev.julioflores.cv.domain;

import java.util.List;
import java.util.Objects;

public record WorkExperience(
        String id,
        String company,
        String position,
        String location,
        Period period,
        String summary,
        List<Phase> phases,
        List<String> highlights) {

    public WorkExperience {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(period, "period");
        phases = Lists.copy(phases);
        highlights = Lists.copy(highlights);
    }
}
