package com.kampive.api.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {
    private final JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.kampive.api");

    @Test
    void coreMustNotDependOnFrameworksOrAdapters() {
        noClasses().that().resideInAnyPackage("..domain..", "..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..", "jakarta..", "org.hibernate..",
                "..adapter..", "..configuration..")
            .allowEmptyShould(true).check(classes);
    }

    @Test
    void domainMustNotDependOnApplication() {
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..application..")
            .allowEmptyShould(true).check(classes);
    }

    @Test
    void modulesMayOnlyConsumeOtherModulesThroughInputContracts() {
        String[] modules = {"identity", "user", "geography", "campsite",
            "review", "favorite", "notification"};
        for (String source : modules) {
            for (String target : modules) {
                if (source.equals(target)) continue;
                noClasses().that().resideInAPackage("..module." + source + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                        "..module." + target + ".adapter..",
                        "..module." + target + ".configuration..",
                        "..module." + target + ".domain..",
                        "..module." + target + ".application.usecase..",
                        "..module." + target + ".application.port.out..")
                    .allowEmptyShould(true).check(classes);
            }
        }
    }
}
