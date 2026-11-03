package com.renaser.os;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El análisis 80/20 resume lo que la persona respondió en el Código Renaser (spec 031, 031-2 A), así que sale por las puertas que se
 * guardan y por ninguna más: la lectura propia, la del mentor, la del líder y SER. Las dos que muestran a OTRA persona preguntan a
 * {@code Interruptores} («Código Renaser en las fichas», spec 034) antes de leer. Si alguien suma una puerta nueva al
 * {@code Analisis8020Finder}, o al {@code RespuestasDelRadarFinder} que saca el texto del Radar, este test se pone rojo: esa puerta también
 * tiene que respetar el interruptor, y quien la agregue lo decide a propósito.
 *
 * <p>Y una revisión automática de FR-024 / SC-006: ningún registro técnico del análisis lleva su contenido.
 */
class AnalisisSoloSaleConLosPermisosDeLaFichaTest {

    private static final String BASE = "com.renaser.os.";
    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.renaser.os");

    @Test
    @DisplayName("el Analisis8020Finder solo lo usan la propia lectura, la del mentor, la del líder y la herramienta de SER")
    void finderDelAnalisis() {
        assertThat(quienesDependenDe(BASE + "rag.api.Analisis8020Finder")).containsExactlyInAnyOrder(
                BASE + "rag.application.services.analisis.ConsultaDelAnalisis8020Service",
                BASE + "rag.application.services.analisis.AnalisisDelAlumnoService",
                BASE + "leadership.application.services.AnalisisDePersonaService",
                BASE + "rag.application.services.herramientas.ConsultarAnalisis8020Herramienta");
    }

    @Test
    @DisplayName("las dos lecturas que muestran a otra persona preguntan a Interruptores («Código Renaser en las fichas»)")
    void lasLecturasDeOtraPersonaPreguntanAlInterruptor() {
        for (String lectura : List.of(BASE + "rag.application.services.analisis.AnalisisDelAlumnoService",
                BASE + "leadership.application.services.AnalisisDePersonaService")) {
            assertThat(CLASES.get(lectura).getDirectDependenciesFromSelf().stream().map(d -> d.getTargetClass().getName())
                    .collect(Collectors.toSet())).as(lectura).contains(BASE + "shared.application.Interruptores");
        }
    }

    @Test
    @DisplayName("el texto del Radar para el análisis lo lee un solo servicio: el que reúne los insumos de la propia persona")
    void respuestasDelRadar() {
        assertThat(quienesDependenDe(BASE + "habits.api.RespuestasDelRadarFinder")).containsExactlyInAnyOrder(
                BASE + "habits.application.services.RespuestasDelRadarService",
                BASE + "rag.application.services.analisis.ReunirInsumosDelAnalisis");
    }

    @Test
    @DisplayName("el caso de uso de la ficha del mentor lo implementa una sola clase")
    void casoDeUsoDelMentor() {
        assertThat(quienesDependenDe(BASE + "rag.application.ports.in.analisis.ConsultarAnalisisDeAlumnoUseCase"))
                .containsExactlyInAnyOrder(BASE + "rag.application.services.analisis.AnalisisDelAlumnoService",
                        BASE + "rag.infrastructure.adapter.in.rest.analisis.AnalisisDelAlumnoController");
    }

    @Test
    @DisplayName("FR-024 / SC-006: ninguna línea de registro técnico del análisis nombra su contenido ni lo que escribió la persona")
    void sinContenidoEnLosRegistros() throws IOException {
        Pattern registro = Pattern.compile("(?<![A-Za-z])log\\.(trace|debug|info|warn|error)\\(");
        Pattern contenido = Pattern.compile("datosEnJson|cifrasEnJson|loQueMasMovio|loQueSeTeFue|comoEstuviste|proximosPasos|queSiento|queHago|"
                + "quePienso|queEvito|notasDeCierre|\\.texto\\(\\)|escritoEn|respuestas\\(\\)");
        List<Path> fuentes;
        try (Stream<Path> arbol = Files.walk(Path.of("src/main/java/com/renaser/os"))) {
            fuentes = arbol.filter(ruta -> ruta.toString().endsWith(".java")).filter(AnalisisSoloSaleConLosPermisosDeLaFichaTest::esDelAnalisis).toList();
        }
        assertThat(fuentes).as("la prueba tiene que encontrar las fuentes del análisis").hasSizeGreaterThan(10);
        for (Path fuente : fuentes) {
            String codigo = Files.readString(fuente);
            for (String linea : codigo.split(";\\s*\\n")) {
                java.util.regex.Matcher llamada = registro.matcher(linea);
                if (llamada.find() && contenido.matcher(linea.substring(llamada.start())).find()) {
                    throw new AssertionError(fuente + " registra contenido del análisis: " + linea.strip());
                }
            }
        }
    }

    private static boolean esDelAnalisis(Path ruta) {
        String nombre = ruta.toString();
        return nombre.contains("/analisis/") || nombre.contains("Analisis8020") || nombre.contains("Pareto8020") || nombre.contains("ArmadoDel8020")
                || nombre.contains("AnalisisDePersona") || nombre.contains("AnalisisDelAlumno") || nombre.contains("RespuestasDelRadar");
    }

    private static Set<String> quienesDependenDe(String nombre) {
        JavaClass destino = CLASES.get(nombre);
        return destino.getDirectDependenciesToSelf().stream()
                .map(d -> d.getOriginClass())
                .filter(origen -> !origen.getName().equals(nombre) && !origen.getName().startsWith(nombre + "$"))
                .map(origen -> origen.getName().contains("$") ? origen.getName().substring(0, origen.getName().indexOf('$')) : origen.getName())
                .collect(Collectors.toSet());
    }
}
