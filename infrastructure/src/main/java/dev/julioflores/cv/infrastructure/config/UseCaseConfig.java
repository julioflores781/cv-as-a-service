package dev.julioflores.cv.infrastructure.config;

import dev.julioflores.cv.application.port.in.GetContactUseCase;
import dev.julioflores.cv.application.port.in.GetEducationUseCase;
import dev.julioflores.cv.application.port.in.GetExperienceUseCase;
import dev.julioflores.cv.application.port.in.GetImpactUseCase;
import dev.julioflores.cv.application.port.in.GetProfileUseCase;
import dev.julioflores.cv.application.port.in.GetResumeUseCase;
import dev.julioflores.cv.application.port.in.GetSkillsUseCase;
import dev.julioflores.cv.application.port.in.TranslateHl7UseCase;
import dev.julioflores.cv.application.port.out.Hl7Translator;
import dev.julioflores.cv.application.port.out.ResumeRepository;
import dev.julioflores.cv.application.service.GetContactService;
import dev.julioflores.cv.application.service.GetEducationService;
import dev.julioflores.cv.application.service.GetExperienceService;
import dev.julioflores.cv.application.service.GetImpactService;
import dev.julioflores.cv.application.service.GetProfileService;
import dev.julioflores.cv.application.service.GetResumeService;
import dev.julioflores.cv.application.service.GetSkillsService;
import dev.julioflores.cv.application.service.TranslateHl7Service;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/** Único punto donde se cablean los casos de uso (application no conoce CDI). */
@ApplicationScoped
public class UseCaseConfig {

    @Produces
    @ApplicationScoped
    GetProfileUseCase getProfile(ResumeRepository resumes) {
        return new GetProfileService(resumes);
    }

    @Produces
    @ApplicationScoped
    GetImpactUseCase getImpact(ResumeRepository resumes) {
        return new GetImpactService(resumes);
    }

    @Produces
    @ApplicationScoped
    GetExperienceUseCase getExperience(ResumeRepository resumes) {
        return new GetExperienceService(resumes);
    }

    @Produces
    @ApplicationScoped
    GetSkillsUseCase getSkills(ResumeRepository resumes) {
        return new GetSkillsService(resumes);
    }

    @Produces
    @ApplicationScoped
    GetEducationUseCase getEducation(ResumeRepository resumes) {
        return new GetEducationService(resumes);
    }

    @Produces
    @ApplicationScoped
    GetContactUseCase getContact(ResumeRepository resumes) {
        return new GetContactService(resumes);
    }

    @Produces
    @ApplicationScoped
    GetResumeUseCase getResume(ResumeRepository resumes) {
        return new GetResumeService(resumes);
    }

    @Produces
    @ApplicationScoped
    TranslateHl7UseCase translateHl7(Hl7Translator translator) {
        return new TranslateHl7Service(translator);
    }
}
