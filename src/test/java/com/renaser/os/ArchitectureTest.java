package com.renaser.os;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Las reglas de sec. 5.1 y sec. 5.4, ejecutables. Si esto pasa a rojo en CI, la arquitectura
 * se rompio de verdad — no es una convencion que dependa de la buena voluntad del equipo.
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.renaser.os");

    @Test
    @DisplayName("los modulos no se acoplan por sus paquetes internos")
    void modulesDoNotLeakInternals() {
        ApplicationModules.of(RenaserOsApplication.class).verify();
    }

    @Test
    @DisplayName("domain/ no conoce Spring, JPA ni Jackson")
    void domainIsFrameworkFree() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "jakarta.validation..",
                        "com.fasterxml.jackson..")
                .because("domain/ debe testearse sin levantar Spring ni Postgres (CLAUDE.MD sec. 5.1)")
                .check(CLASSES);
    }

    @Test
    @DisplayName("domain/ no conoce a los adaptadores")
    void domainDoesNotDependOnAdapters() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..")
                .because("las dependencias apuntan hacia adentro (CLAUDE.MD sec. 5.1.1)")
                .check(CLASSES);
    }

    @Test
    @DisplayName("application/ no conoce HTTP")
    void applicationDoesNotKnowHttp() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.servlet..",
                        "org.springframework.web..",
                        "org.springframework.http..")
                .because("el caso de uso no conoce el transporte (CLAUDE.MD sec. 5.4.6)")
                // TODO quitar allowEmptyShould cuando exista el primer caso de uso
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("el controller no toca repositorios ni puertos de salida")
    void controllersDoNotTouchPersistence() {
        noClasses()
                .that().resideInAPackage("..adapter.in.web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..port.out..",
                        "..adapter.out..",
                        "org.springframework.data..",
                        "jakarta.persistence..")
                .because("el controller solo invoca casos de uso (CLAUDE.MD sec. 5.4.6)")
                // TODO quitar allowEmptyShould cuando exista el primer controller
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("no hay clases basurero: Util, Helper, Manager")
    void noJunkDrawerClasses() {
        noClasses()
                .that().resideInAPackage("com.renaser.os..")
                .should().haveSimpleNameEndingWith("Util")
                .orShould().haveSimpleNameEndingWith("Utils")
                .orShould().haveSimpleNameEndingWith("Helper")
                .orShould().haveSimpleNameEndingWith("Manager")
                .because("son nombres que no dicen nada y se vuelven basureros (CLAUDE.MD sec. 5.4.8)")
                .check(CLASSES);
    }
}
