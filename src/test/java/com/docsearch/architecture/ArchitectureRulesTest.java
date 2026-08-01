package com.docsearch.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Executable guardrails for the Clean Architecture layering.
 *
 * <p>These exist from Day 1 on purpose: the layering is easy to state now and hard
 * to retrofit once persistence and search land. Rules covering packages that do not
 * exist yet pass vacuously — see {@code src/test/resources/archunit.properties}.
 */
@AnalyzeClasses(packages = "com.docsearch", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule controllers_live_in_the_api_package =
            classes().that().areAnnotatedWith(RestController.class)
                    .should().resideInAPackage("..api..");

    @ArchTest
    static final ArchRule the_api_layer_must_not_reach_into_infrastructure =
            noClasses().that().resideInAPackage("..api..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule services_must_not_depend_on_the_api_layer =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..api..");

    @ArchTest
    static final ArchRule the_domain_must_stay_framework_free =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..", "com.mongodb..", "org.opensearch..");

    @ArchTest
    static final ArchRule packages_must_be_free_of_cycles =
            slices().matching("com.docsearch.(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule no_java_util_logging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    @ArchTest
    static final ArchRule constructor_injection_only = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
}
