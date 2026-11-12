package dev.julioflores.cv.infrastructure;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import dev.julioflores.cv.application.error.ApplicationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private static final String DOMAIN = "dev.julioflores.cv.domain..";
    private static final String APPLICATION = "dev.julioflores.cv.application..";
    private static final String INFRASTRUCTURE = "dev.julioflores.cv.infrastructure..";
    private static final String ADAPTER_IN = "dev.julioflores.cv.infrastructure.adapter.in..";
    private static final String ERROR_PACKAGE = "dev.julioflores.cv.infrastructure.adapter.in.rest.error..";
    private static final String ADAPTER_OUT = "dev.julioflores.cv.infrastructure.adapter.out..";
    private static final String[] FRAMEWORKS = {
        "jakarta..", "io.quarkus..", "com.fasterxml..", "org.eclipse.microprofile.."
    };

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.julioflores.cv");
    }

    @Test
    void importsClassesFromAllThreeModules() {
        // Evita el falso positivo de reglas que pasan porque no analizan nada.
        assertFalse(classes.that(resideIn(DOMAIN)).isEmpty(), "domain not imported");
        assertFalse(classes.that(resideIn(APPLICATION)).isEmpty(), "application not imported");
        assertFalse(classes.that(resideIn(INFRASTRUCTURE)).isEmpty(), "infrastructure not imported");
    }

    @Test
    void domainDoesNotDependOnOtherLayers() {
        noClasses().that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAnyPackage(APPLICATION, INFRASTRUCTURE)
                .check(classes);
    }

    @Test
    void domainIsFrameworkFree() {
        noClasses().that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
                .check(classes);
    }

    @Test
    void applicationOnlyKnowsTheDomain() {
        noClasses().that().resideInAPackage(APPLICATION)
                .should().dependOnClassesThat().resideInAnyPackage(INFRASTRUCTURE)
                .orShould().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
                .check(classes);
    }

    @Test
    void inboundAdaptersDoNotUseOutboundOnes() {
        noClasses().that().resideInAPackage(ADAPTER_IN)
                .should().dependOnClassesThat().resideInAPackage(ADAPTER_OUT)
                .check(classes);
    }

    @Test
    void outboundAdaptersDoNotUseInboundOnes() {
        noClasses().that().resideInAPackage(ADAPTER_OUT)
                .should().dependOnClassesThat().resideInAPackage(ADAPTER_IN)
                .check(classes);
    }

    @Test
    void everyBusinessExceptionExtendsApplicationException() {
        classes().that().areAssignableTo(RuntimeException.class)
                .and().resideInAPackage(APPLICATION)
                .should().beAssignableTo(ApplicationException.class)
                .check(classes);
    }

    @Test
    void onlyTheErrorPackageMapsExceptionsToHttp() {
        noClasses().that().resideOutsideOfPackage(ERROR_PACKAGE)
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("org.jboss.resteasy.reactive.server.ServerExceptionMapper")
                .check(classes);
    }

    @Test
    void businessErrorTextLivesOnlyInTheErrorPackage() {
        noClasses().that().resideOutsideOfPackage(ERROR_PACKAGE)
                .should().dependOnClassesThat().haveSimpleName("ProblemMessages")
                .check(classes);
    }

    private static final String SECURITY_ADAPTER = "dev.julioflores.cv.infrastructure.adapter.out.security..";
    private static final String CONFIG = "dev.julioflores.cv.infrastructure.config..";

    @Test
    void cryptoLibrariesLiveOnlyInOutboundAdaptersAndConfig() { // T121 (parte auth)
        noClasses().that().resideOutsideOfPackages(ADAPTER_OUT, CONFIG)
                .should().dependOnClassesThat().resideInAnyPackage("software.amazon..", "org.bouncycastle..")
                .check(classes);
    }

    @Test
    void domainAndApplicationDoNotUseCryptoOrJwtLibraries() { // T121 (parte auth)
        noClasses().that().resideInAnyPackage(DOMAIN, APPLICATION)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "software.amazon..", "org.bouncycastle..", "io.smallrye.jwt..", "org.eclipse.microprofile.jwt..")
                .check(classes);
    }

    @Test
    void onlyTheSecurityAdapterTouchesArgon2AndJwtBuilding() { // T121 (parte auth)
        noClasses().that().resideOutsideOfPackage(SECURITY_ADAPTER)
                .should().dependOnClassesThat().resideInAnyPackage("org.bouncycastle..", "io.smallrye.jwt.build..")
                .check(classes);
    }

    @Test
    void theSecurityAdapterIsActuallyAnalysed() {
        // Evita el falso positivo de las reglas anteriores: el adaptador existe y usa de verdad esas librerías.
        assertFalse(classes.that(resideIn(SECURITY_ADAPTER)).isEmpty(), "adapter.out.security not imported");
    }

    @Test
    void theAwsSdkIsOnlyUsedByTheStorageAdapterAndItsClientConfig() { // T121 (parte imágenes)
        noClasses().that().resideOutsideOfPackages(ADAPTER_OUT, CONFIG)
                .should().dependOnClassesThat().resideInAnyPackage("software.amazon..")
                .check(classes);
        noClasses().that().resideInAnyPackage(DOMAIN, APPLICATION, ADAPTER_IN)
                .should().dependOnClassesThat().resideInAnyPackage("software.amazon..")
                .check(classes);
        // Anti falso positivo: el adaptador existe y de verdad usa el SDK.
        assertFalse(classes.that(resideIn("dev.julioflores.cv.infrastructure.adapter.out.storage..")).isEmpty());
        assertTrue(classes.that(resideIn("dev.julioflores.cv.infrastructure.adapter.out.storage.."))
                .stream().anyMatch(c -> c.getDirectDependenciesFromSelf().stream()
                        .anyMatch(d -> d.getTargetClass().getPackageName().startsWith("software.amazon"))));
    }

    private static DescribedPredicate<JavaClass> resideIn(String pkg) {
        return JavaClass.Predicates.resideInAPackage(pkg);
    }
}
