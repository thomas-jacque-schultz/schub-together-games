package schultz.thomas.schub.games;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

// La règle de package-info.java : api → business.
@AnalyzeClasses(packages = "schultz.thomas.schub.games", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule businessIgnoreApi = noClasses().that().resideInAPackage("schultz.thomas.schub.games.business..")
            .should().dependOnClassesThat().resideInAnyPackage("schultz.thomas.schub.games.api..",
                    "schultz.thomas.schub.games.config..", "org.springframework.web..",
                    "com.fasterxml.jackson..")
            .because("business ne connaît ni le WebSocket ni le JSON ; les envois passent par Delivery");
}
