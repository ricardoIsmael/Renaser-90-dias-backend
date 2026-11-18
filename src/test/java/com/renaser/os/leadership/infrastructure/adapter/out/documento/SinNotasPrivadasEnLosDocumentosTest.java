package com.renaser.os.leadership.infrastructure.adapter.out.documento;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que ningún documento puede llevar, comprobado en el código y no solo en una prueba de datos (spec 056, FR-037 y SC-007): la nota
 * privada del mentor y un plazo escrito a mano.
 */
class SinNotasPrivadasEnLosDocumentosTest {

    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.renaser.os.leadership");

    @Test
    @DisplayName("spec 056 FR-037 SC-007: ninguna clase que arma, genera o entrega un documento conoce las notas privadas del mentor")
    void nadieDelDocumentoLeeLasNotas() {
        noClasses().that().haveSimpleNameContaining("Documento").or().haveSimpleNameContaining("Plantilla")
                .or().resideInAPackage("..domain.model.documento..")
                .should().dependOnClassesThat().haveSimpleNameContaining("NotasDelMentor")
                .orShould().dependOnClassesThat().haveSimpleNameContaining("NotaDelAprendiz")
                .because("la nota privada del mentor nunca sale en un documento (FR-037): ni el armado de los datos ni la plantilla la leen")
                .check(CLASES);
    }

    @Test
    @DisplayName("spec 056 FR-035: la plantilla no escribe «90 días» a mano: el plazo sale de los datos de la formación de la persona")
    void laPlantillaNoEscribeNoventaDias() throws Exception {
        List<Path> fuentes;
        try (var archivos = Files.list(Path.of("src/main/java/com/renaser/os/leadership/infrastructure/adapter/out/documento"))) {
            fuentes = archivos.filter(p -> p.toString().endsWith(".java")).toList();
        }

        assertThat(fuentes).isNotEmpty();
        for (Path fuente : fuentes) {
            assertThat(Files.readString(fuente)).as(fuente.getFileName().toString()).doesNotContainPattern("(?i)\\b90\\s*d[ií]as");
        }
    }
}
