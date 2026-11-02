package dev.julioflores.cv.domain;

import java.util.List;
import java.util.Objects;

public record SkillGroup(String key, String name, List<String> keywords) {

    public SkillGroup {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(name, "name");
        keywords = Lists.copy(keywords);
    }
}
