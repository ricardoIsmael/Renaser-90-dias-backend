package com.renaser.os;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El Código Renaser de OTRA persona sale por tres lecturas y solo tres (spec 034, D-299): la del mentor y la guía
 * ({@code AcompanamientoDeAlumnoService}) y las dos del líder, Administración y Alquimista ({@code RadarDePersonaService}).
 * Las tres preguntan a {@code Interruptores} («Código Renaser en las fichas») antes de leer. Si alguien suma una puerta nueva a
 * {@code LecturaDelRadar} o al {@code CodigoRenaserDePersonaFinder}, este test se pone rojo: esa puerta también tiene que
 * respetar el interruptor, y quien la agregue lo decide a propósito.
 */
class CodigoRenaserSoloPorLasTresLecturasTest {

    private static final String BASE = "com.renaser.os.";
    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.renaser.os");

    @Test
    @DisplayName("solo el autoservicio, la lectura del mentor y el buscador del líder usan LecturaDelRadar")
    void lecturaDelRadar() {
        assertThat(quienesDependenDe(BASE + "habits.application.services.LecturaDelRadar")).containsExactlyInAnyOrder(
                BASE + "habits.application.services.RadarService",
                BASE + "habits.application.services.AcompanamientoDeAlumnoService",
                BASE + "habits.application.services.CodigoRenaserDePersonaService");
    }

    @Test
    @DisplayName("solo la lectura del líder y la implementación del contrato dependen del CodigoRenaserDePersonaFinder")
    void finderDelLider() {
        assertThat(quienesDependenDe(BASE + "habits.api.CodigoRenaserDePersonaFinder")).containsExactlyInAnyOrder(
                BASE + "habits.application.services.CodigoRenaserDePersonaService",
                BASE + "leadership.application.services.RadarDePersonaService");
    }

    @Test
    @DisplayName("las dos lecturas de otra persona preguntan a Interruptores")
    void lasLecturasPreguntanAlInterruptor() {
        for (String lectura : List.of(BASE + "habits.application.services.AcompanamientoDeAlumnoService",
                BASE + "leadership.application.services.RadarDePersonaService")) {
            assertThat(CLASES.get(lectura).getDirectDependenciesFromSelf().stream()
                    .map(d -> d.getTargetClass().getName()).collect(Collectors.toSet()))
                    .as(lectura).contains(BASE + "shared.application.Interruptores");
        }
    }

    @Test
    @DisplayName("el caso de uso del mentor lo implementa una sola clase")
    void casoDeUsoDelMentor() {
        assertThat(quienesDependenDe(BASE + "habits.application.ports.in.acompanamiento.ConsultarCodigoRenaserDeAlumnoUseCase"))
                .containsExactlyInAnyOrder(BASE + "habits.application.services.AcompanamientoDeAlumnoService",
                        BASE + "habits.infrastructure.adapter.in.rest.acompanamiento.CodigoRenaserDelAlumnoController");
    }

    private static Set<String> quienesDependenDe(String nombre) {
        JavaClass destino = CLASES.get(nombre);
        return destino.getDirectDependenciesToSelf().stream()
                .map(d -> d.getOriginClass())
                .filter(origen -> !origen.getName().equals(nombre) && !origen.getName().startsWith(nombre + "$"))
                .map(JavaClass::getName)
                .collect(Collectors.toSet());
    }
}
