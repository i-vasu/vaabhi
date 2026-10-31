package com.vaabhi;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

/** Coding and layering rules on production code; module boundaries are checked by {@link ModularityTests}. */
class ArchitectureTests {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.vaabhi");

    @Test
    void usesConstructorInjection() {
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(CLASSES);
    }

    @Test
    void logsThroughSlf4jOnly() {
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(CLASSES);
        NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(CLASSES);
    }

    @Test
    void controllersAreNamedAccordingly() {
        classes().that().areAnnotatedWith(RestController.class)
                .should().haveSimpleNameEndingWith("Controller")
                .check(CLASSES);
    }

    @Test
    void controllersGoThroughServices() {
        noClasses().that().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().areAssignableTo(Repository.class)
                .because("controllers call services, which own transactions and caching")
                .check(CLASSES);
    }

    @Test
    void controllersExposeDtosNotEntities() {
        noClasses().that().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
                .because("JPA entities are internal; the API returns records/DTOs")
                .check(CLASSES);
    }
}
