package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

public class SkillGroupNotFoundException extends ApplicationException {

    public SkillGroupNotFoundException(String group) {
        super(ErrorCode.SKILL_GROUP_NOT_FOUND, "No skill group '" + group + "'", group);
    }
}
