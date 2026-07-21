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
                    "org.springframework..",
                    "jakarta.persistence..")
            .because("the domain must remain independent from use cases and technical frameworks");

    @ArchTest
    static final ArchRule application_is_independent_from_driven_and_driving_adapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..interfaces..",
                    "..infrastructure..")
            .because("application use cases must communicate with adapters through ports");
}
