package com.renaser.os.onboarding.domain.model.diagnostico;

import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.onboarding.domain.model.diagnostico.LecturaDeImportacion.PreguntaImportada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlantillaDeImportacionTest {

    private static final String CABECERA = String.join(",", PlantillaDeImportacion.COLUMNAS.stream().map(c -> c.contains(",") ? "\"" + c + "\"" : c).toList());

    private static LecturaDeImportacion leer(String filas) {
        return PlantillaDeImportacion.leer(CABECERA + "\n" + filas, Map.of());
    }

    @Test
    @DisplayName("el anexo diagnostico-inicial.csv se lee entero: 6 secciones, 27 preguntas, su condición, su máximo, su cuadrícula y sus destinos")
    void elFormularioDelDueno() throws IOException {
        String csv = Files.readString(Path.of("specs/026-ficha-con-diagnostico/diagnostico-inicial.csv"), StandardCharsets.UTF_8);

        LecturaDeImportacion lectura = PlantillaDeImportacion.leer(csv, Map.of());

        assertThat(lectura.errores()).isEmpty();
        assertThat(lectura.secciones()).hasSize(6);
        assertThat(lectura.preguntas()).hasSize(27);
        PreguntaImportada p13a = porClave(lectura, "p13a");
        assertThat(p13a.condicionClave()).isEqualTo("p13");
        assertThat(p13a.condicionValor()).isEqualTo("Sí.");
        assertThat(porClave(lectura, "p14").pregunta().maximoOpciones()).isEqualTo(2);
        assertThat(porClave(lectura, "p14").tipo()).isEqualTo(TipoPreguntaOnboarding.SELECCION_MULTIPLE);
        assertThat(porClave(lectura, "p5").tipo()).isEqualTo(TipoPreguntaOnboarding.CUADRICULA);
        assertThat(porClave(lectura, "p5").pregunta().opciones()).hasSize(7);
        assertThat(porClave(lectura, "p23").tipo()).isEqualTo(TipoPreguntaOnboarding.CASILLA);
        assertThat(porClave(lectura, "p16").pregunta().alimenta()).isEqualTo(DestinoDeRespuesta.OBJETIVO_RELACIONES);
        assertThat(porClave(lectura, "p17").pregunta().alimenta()).isEqualTo(DestinoDeRespuesta.OBJETIVO_CUERPO);
        assertThat(porClave(lectura, "p18").pregunta().alimenta()).isEqualTo(DestinoDeRespuesta.OBJETIVO_NEGOCIO);
        assertThat(porClave(lectura, "p19").pregunta().alimenta()).isEqualTo(DestinoDeRespuesta.PRIORIDAD_PRINCIPAL);
        assertThat(porClave(lectura, "p19").pregunta().opciones()).extracting(OpcionDelDiagnostico::valor)
                .containsExactly("RELACIONES", "CUERPO", "TRABAJO");
        assertThat(porClave(lectura, "p21").pregunta().alimenta()).isEqualTo(DestinoDeRespuesta.PRIMERA_ACCION);
        assertThat(porClave(lectura, "p5").pregunta().ayuda()).contains("\n");
    }

    @Test
    @DisplayName("lee coma, punto y coma o tabulador (lo que sale al copiar celdas de Excel o Google Sheets)")
    void separadores() {
        String fila = "A,p1,¿Nombre?,,Texto corto,Sí,,,,,";
        assertThat(PlantillaDeImportacion.leer(CABECERA + "\n" + fila, Map.of()).errores()).isEmpty();
        String conPuntoYComa = (CABECERA + "\n" + fila).replace(",", ";").replace("(separadas por |)", "(separadas por |)");
        assertThat(PlantillaDeImportacion.leer(conPuntoYComa, Map.of()).preguntas()).hasSize(1);
        String conTabs = (CABECERA + "\n" + fila).replace(",", "\t");
        assertThat(PlantillaDeImportacion.leer(conTabs, Map.of()).preguntas()).hasSize(1);
    }

    @Test
    @DisplayName("una celda entre comillas puede traer comas, comillas y saltos de línea")
    void celdasConComillas() {
        LecturaDeImportacion lectura = leer("A,p1,\"¿Cómo, exactamente, \"\"te\"\" sientes?\",\"Línea uno\nLínea dos\",Párrafo,No,,,,,");
        assertThat(lectura.errores()).isEmpty();
        assertThat(lectura.preguntas().get(0).pregunta().texto()).isEqualTo("¿Cómo, exactamente, \"te\" sientes?");
        assertThat(lectura.preguntas().get(0).pregunta().ayuda()).isEqualTo("Línea uno\nLínea dos");
    }

    @Test
    @DisplayName("el BOM del principio (lo que guarda Excel) no estorba la primera columna")
    void bom() {
        assertThat(PlantillaDeImportacion.leer("\uFEFF" + CABECERA + "\nA,p1,¿Nombre?,,Texto corto,Sí,,,,,", Map.of()).errores()).isEmpty();
    }

    @Test
    @DisplayName("los errores salen por número de fila, con el motivo, y no se pierde ninguno")
    void erroresPorFila() {
        LecturaDeImportacion lectura = leer(String.join("\n",
                "A,p1,¿Nombre?,,Texto corto,Sí,,,,,",
                "A,p2,¿Algo?,,Casilla,Sí,,,,,",
                "A,p3,¿Elige?,,Opción única,Quizá,,,,,",
                "A,p4,¿Cuál?,,Opción única,Sí,Uno | Dos,,p9=Uno,,",
                "A,p5,,,Texto corto,Sí,,,,,"));

        assertThat(lectura.errores()).extracting(e -> e.fila()).contains(3, 4, 5, 6);
        assertThat(lectura.errores().stream().filter(e -> e.fila() == 3).findFirst().orElseThrow().mensaje()).contains("no existe").contains("Casillas");
        assertThat(lectura.errores().stream().filter(e -> e.fila() == 4).findFirst().orElseThrow().mensaje()).contains("Sí o No");
        assertThat(lectura.errores().stream().filter(e -> e.fila() == 5).findFirst().orElseThrow().mensaje()).contains("p9");
        assertThat(lectura.tieneErrores()).isTrue();
    }

    @Test
    @DisplayName("una columna que no existe en la plantilla o una obligatoria que falta se informan en la fila 1")
    void columnas() {
        LecturaDeImportacion rara = PlantillaDeImportacion.leer("Sección,Pregunta,Tipo,Color\nA,¿Hola?,Texto corto,rojo", Map.of());
        assertThat(rara.errores()).anyMatch(e -> e.fila() == 1 && e.mensaje().contains("Color"));
        LecturaDeImportacion sinTipo = PlantillaDeImportacion.leer("Sección,Pregunta\nA,¿Hola?", Map.of());
        assertThat(sinTipo.errores()).anyMatch(e -> e.mensaje().contains("Falta la columna «Tipo»"));
    }

    @Test
    @DisplayName("dos preguntas con el mismo destino, o una clave repetida, se rechazan; un texto repetido solo avisa")
    void repetidos() {
        LecturaDeImportacion lectura = leer(String.join("\n",
                "A,p1,¿Uno?,,Párrafo,Sí,,,,Objetivo de Cuerpo,",
                "A,p2,¿Dos?,,Párrafo,Sí,,,,Objetivo de Cuerpo,",
                "A,p1,¿Tres?,,Texto corto,Sí,,,,,",
                "A,p4,¿Uno?,,Párrafo,Sí,,,,,"));
        assertThat(lectura.errores()).anyMatch(e -> e.fila() == 3 && e.mensaje().contains("fila 2"));
        assertThat(lectura.errores()).anyMatch(e -> e.fila() == 4 && e.mensaje().contains("ya la usa la fila 2"));
        assertThat(lectura.avisos()).anyMatch(a -> a.fila() == 5);
    }

    @Test
    @DisplayName("una condición puede apuntar a una pregunta que ya estaba en el diagnóstico")
    void condicionContraLoExistente() {
        Map<String, List<OpcionDelDiagnostico>> existentes = Map.of("p9", List.of(OpcionDelDiagnostico.de("Sí."), OpcionDelDiagnostico.de("No.")));
        LecturaDeImportacion lectura = PlantillaDeImportacion.leer(CABECERA + "\nA,p10,¿Y entonces?,,Texto corto,No,,,p9=Sí,,", existentes);
        assertThat(lectura.errores()).isEmpty();
        assertThat(lectura.preguntas().get(0).condicionClave()).isEqualTo("p9");
    }

    @Test
    @DisplayName("la formación, la escala y el máximo se leen; la formación que no existe se rechaza")
    void formacionesYEscala() {
        LecturaDeImportacion lectura = leer(String.join("\n",
                "A,p1,¿Cuánto?,,Escala,Sí,1-10,,,,Diplomado | Certificación",
                "A,p2,¿Dónde?,,Texto corto,Sí,,,,,Universidad"));
        assertThat(lectura.preguntas().get(0).pregunta().escala().max()).isEqualTo(10);
        assertThat(lectura.preguntas().get(0).formaciones()).hasSize(2);
        assertThat(lectura.errores()).anyMatch(e -> e.fila() == 3 && e.mensaje().contains("Universidad"));
    }

    @Test
    @DisplayName("exportar y volver a leer da las mismas preguntas")
    void exportarYLeer() {
        PreguntaDelDiagnostico p = PreguntaDelDiagnostico.nueva(1, 0, "diag_p1", "¿Cuál?", "Elige una", TipoPreguntaOnboarding.SELECCION_UNICA, true, null,
                List.of(new OpcionDelDiagnostico("CUERPO", "Salud."), new OpcionDelDiagnostico("TRABAJO", "Negocios.")), null, null,
                DestinoDeRespuesta.PRIORIDAD_PRINCIPAL, java.util.Set.of(), null);
        FichaDelDiagnostico ficha = new FichaDelDiagnostico(List.of(new SeccionDelDiagnostico(1, "s1", "Primera", null, 0, List.of(p))));

        LecturaDeImportacion vuelta = PlantillaDeImportacion.leer(PlantillaDeImportacion.exportar(ficha), Map.of());

        assertThat(vuelta.errores()).isEmpty();
        assertThat(vuelta.preguntas().get(0).pregunta().opciones()).extracting(OpcionDelDiagnostico::valor).containsExactly("CUERPO", "TRABAJO");
        assertThat(vuelta.preguntas().get(0).pregunta().alimenta()).isEqualTo(DestinoDeRespuesta.PRIORIDAD_PRINCIPAL);
    }

    @Test
    @DisplayName("la plantilla que se descarga es válida por sí misma")
    void laPlantillaEsValida() {
        LecturaDeImportacion lectura = PlantillaDeImportacion.leer(PlantillaDeImportacion.plantilla(), Map.of());
        assertThat(lectura.errores()).isEmpty();
        assertThat(lectura.preguntas()).hasSize(3);
    }

    private static PreguntaImportada porClave(LecturaDeImportacion lectura, String clave) {
        return lectura.preguntas().stream().filter(p -> clave.equals(p.clave())).findFirst().orElseThrow();
    }
}
