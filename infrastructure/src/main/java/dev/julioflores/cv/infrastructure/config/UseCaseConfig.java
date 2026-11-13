package dev.julioflores.cv.infrastructure.config;

import dev.julioflores.cv.application.port.in.ChangeAdminPasswordUseCase;
import dev.julioflores.cv.application.port.in.CleanupOrphanImagesUseCase;
import dev.julioflores.cv.application.port.in.DeleteImageUseCase;
import dev.julioflores.cv.application.port.in.ListImagesUseCase;
import dev.julioflores.cv.application.port.in.UploadProjectImageUseCase;
import dev.julioflores.cv.application.port.in.GetArchitectureUseCase;
import dev.julioflores.cv.application.port.in.GetContentVersionUseCase;
import dev.julioflores.cv.application.port.in.GetContactUseCase;
import dev.julioflores.cv.application.port.in.GetEditableContentUseCase;
import dev.julioflores.cv.application.port.in.GetEducationUseCase;
import dev.julioflores.cv.application.port.in.GetExperienceUseCase;
import dev.julioflores.cv.application.port.in.GetImpactUseCase;
import dev.julioflores.cv.application.port.in.GetProfileUseCase;
import dev.julioflores.cv.application.port.in.GetProjectsUseCase;
import dev.julioflores.cv.application.port.in.GetResumeUseCase;
import dev.julioflores.cv.application.port.in.GetServicesUseCase;
import dev.julioflores.cv.application.port.in.GetSkillsUseCase;
import dev.julioflores.cv.application.port.in.ListContactMessagesUseCase;
import dev.julioflores.cv.application.port.in.ListContentVersionsUseCase;
import dev.julioflores.cv.application.port.in.LoginAdminUseCase;
import dev.julioflores.cv.application.port.in.LogoutAllSessionsUseCase;
import dev.julioflores.cv.application.port.in.RollbackContentUseCase;
import dev.julioflores.cv.application.port.in.SaveEditableContentUseCase;
import dev.julioflores.cv.application.port.in.SubmitContactMessageUseCase;
import dev.julioflores.cv.application.port.in.TranslateHl7UseCase;
import dev.julioflores.cv.application.port.in.VerifyAdminSessionUseCase;
import dev.julioflores.cv.application.port.out.AdminAccountRepository;
import dev.julioflores.cv.application.port.out.AuditLog;
import dev.julioflores.cv.application.port.out.ContactMessageRepository;
import dev.julioflores.cv.application.port.out.ContactNotifier;
import dev.julioflores.cv.application.port.out.ContentCacheInvalidator;
import dev.julioflores.cv.application.port.out.ContentVersionRepository;
import dev.julioflores.cv.application.port.out.FileStoragePort;
import dev.julioflores.cv.application.port.out.Hl7Translator;
import dev.julioflores.cv.application.port.out.ImageReferenceRepository;
import dev.julioflores.cv.application.port.out.PasswordHasher;
import dev.julioflores.cv.application.port.out.RateLimiter;
import dev.julioflores.cv.application.port.out.ResumeRepository;
import dev.julioflores.cv.application.port.out.SessionTokenIssuer;
import dev.julioflores.cv.application.port.out.SiteContentRepository;
import dev.julioflores.cv.application.service.ChangeAdminPasswordService;
import dev.julioflores.cv.application.service.CleanupOrphanImagesService;
import dev.julioflores.cv.application.service.DeleteImageService;
import dev.julioflores.cv.application.service.ListImagesService;
import dev.julioflores.cv.application.service.UploadProjectImageService;
import dev.julioflores.cv.application.service.GetArchitectureService;
import dev.julioflores.cv.application.service.GetContentVersionService;
import dev.julioflores.cv.application.service.GetContactService;
import dev.julioflores.cv.application.service.GetEditableContentService;
import dev.julioflores.cv.application.service.GetEducationService;
import dev.julioflores.cv.application.service.GetExperienceService;
import dev.julioflores.cv.application.service.GetImpactService;
import dev.julioflores.cv.application.service.GetProfileService;
import dev.julioflores.cv.application.service.GetProjectsService;
import dev.julioflores.cv.application.service.GetResumeService;
import dev.julioflores.cv.application.service.GetServicesService;
import dev.julioflores.cv.application.service.GetSkillsService;
import dev.julioflores.cv.application.service.ListContactMessagesService;
import dev.julioflores.cv.application.service.ListContentVersionsService;
import dev.julioflores.cv.application.service.LoginAdminService;
import dev.julioflores.cv.application.service.LogoutAllSessionsService;
import dev.julioflores.cv.application.service.RollbackContentService;
import dev.julioflores.cv.application.service.SaveEditableContentService;
import dev.julioflores.cv.application.service.SubmitContactMessageService;
import dev.julioflores.cv.application.service.TranslateHl7Service;
import dev.julioflores.cv.application.service.VerifyAdminSessionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import java.time.Clock;

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

    @Produces
    @ApplicationScoped
    ListContactMessagesUseCase listContactMessages(ContactMessageRepository messages, AuditLog audit) {
        return new ListContactMessagesService(messages, audit);
    }

    @Produces
    @ApplicationScoped
    SubmitContactMessageUseCase submitContactMessage(
            ContactMessageRepository messages,
            ContactNotifier notifier,
            @Named(RateLimiterConfig.CONTACT) RateLimiter rateLimiter) {
        return new SubmitContactMessageService(messages, notifier, rateLimiter);
    }

    @Produces
    @ApplicationScoped
    GetServicesUseCase getServices(SiteContentRepository content) {
        return new GetServicesService(content);
    }

    @Produces
    @ApplicationScoped
    GetProjectsUseCase getProjects(SiteContentRepository content) {
        return new GetProjectsService(content);
    }

    @Produces
    @ApplicationScoped
    GetArchitectureUseCase getArchitecture(SiteContentRepository content) {
        return new GetArchitectureService(content);
    }

    @Produces
    @ApplicationScoped
    Clock clock() {
        return Clock.systemUTC();
    }

    @Produces
    @ApplicationScoped
    LoginAdminUseCase loginAdmin(
            AdminAccountRepository accounts,
            PasswordHasher hasher,
            SessionTokenIssuer issuer,
            @Named(RateLimiterConfig.ADMIN_LOGIN) RateLimiter globalLimiter,
            AuditLog audit,
            Clock clock,
            AdminConfig config) {
        return new LoginAdminService(
                accounts, hasher, issuer, globalLimiter, audit, clock, config.maxFailures(), config.lock());
    }

    @Produces
    @ApplicationScoped
    VerifyAdminSessionUseCase verifyAdminSession(AdminAccountRepository accounts) {
        return new VerifyAdminSessionService(accounts);
    }

    @Produces
    @ApplicationScoped
    ChangeAdminPasswordUseCase changeAdminPassword(
            AdminAccountRepository accounts,
            PasswordHasher hasher,
            SessionTokenIssuer issuer,
            AuditLog audit,
            Clock clock,
            AdminConfig config) {
        return new ChangeAdminPasswordService(accounts, hasher, issuer, audit, clock, config.maxFailures(), config.lock());
    }

    @Produces
    @ApplicationScoped
    LogoutAllSessionsUseCase logoutAllSessions(AdminAccountRepository accounts, AuditLog audit) {
        return new LogoutAllSessionsService(accounts, audit);
    }

    @Produces
    @ApplicationScoped
    GetEditableContentUseCase getEditableContent(ContentVersionRepository versions) {
        return new GetEditableContentService(versions);
    }

    @Produces
    @ApplicationScoped
    SaveEditableContentUseCase saveEditableContent(
            ContentVersionRepository versions, ContentCacheInvalidator cache, AuditLog audit) {
        return new SaveEditableContentService(versions, cache, audit);
    }

    @Produces
    @ApplicationScoped
    ListContentVersionsUseCase listContentVersions(ContentVersionRepository versions) {
        return new ListContentVersionsService(versions);
    }

    @Produces
    @ApplicationScoped
    GetContentVersionUseCase getContentVersion(ContentVersionRepository versions) {
        return new GetContentVersionService(versions);
    }

    @Produces
    @ApplicationScoped
    RollbackContentUseCase rollbackContent(
            ContentVersionRepository versions, ContentCacheInvalidator cache, AuditLog audit) {
        return new RollbackContentService(versions, cache, audit);
    }

    @Produces
    @ApplicationScoped
    UploadProjectImageUseCase uploadProjectImage(FileStoragePort storage, AuditLog audit) {
        return new UploadProjectImageService(storage, audit);
    }

    @Produces
    @ApplicationScoped
    ListImagesUseCase listImages(FileStoragePort storage, ImageReferenceRepository references) {
        return new ListImagesService(storage, references);
    }

    @Produces
    @ApplicationScoped
    DeleteImageUseCase deleteImage(
            FileStoragePort storage, ImageReferenceRepository references, AuditLog audit) {
        return new DeleteImageService(storage, references, audit);
    }

    @Produces
    @ApplicationScoped
    CleanupOrphanImagesUseCase cleanupOrphanImages(
            FileStoragePort storage, ImageReferenceRepository references, AuditLog audit, Clock clock) {
        return new CleanupOrphanImagesService(storage, references, audit, clock);
    }
}
