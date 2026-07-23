package com.takibo.managementservice.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.takibo.managementservice")
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domain_is_independent_from_outer_layers = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application..",
                    "..interfaces..",
                    "..infrastructure..",
                    "..integration..",
                    "org.springframework..",
                    "jakarta.persistence..")
            .because("the domain must remain independent from use cases and technical frameworks");

    // Les adaptateurs TMS sont visés par leur chemin qualifié : le glob "..integration.."
    // capturerait aussi les ports d'intégration publiés par TIS-CORE (couture métriques
    // dashboard), qui sont un contrat cross-module légitime pour la couche application.
    @ArchTest
    static final ArchRule application_depends_on_adapters_only_through_ports = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.takibo.managementservice.interfaces..",
                    "com.takibo.managementservice.infrastructure..",
                    "com.takibo.managementservice.integration..")
            .because("application use cases must communicate with TMS adapters (driving, driven, cross-module) through ports");

    @ArchTest
    static final ArchRule application_does_not_leak_into_other_module_internals = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.takibo.identitycore.application..",
                    "com.takibo.identitycore.domain..")
            .because("cross-module access must go through TIS-CORE integration ports, never its application or domain internals");

    @ArchTest
    static final ArchRule application_does_not_serialize_or_touch_outbox_internals = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.fasterxml.jackson..",
                    "com.takibo.outbox..")
            .because("serialization and outbox enveloping are infrastructure concerns behind a publisher port");

    @ArchTest
    static final ArchRule adapters_do_not_reside_in_application = noClasses()
            .that().haveSimpleNameEndingWith("Adapter")
            .should().resideInAPackage("..application..")
            .because("adapters are driven/driving/integration components and must live outside the application layer");
}
