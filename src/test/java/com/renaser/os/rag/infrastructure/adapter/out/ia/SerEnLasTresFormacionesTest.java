package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.mapa.ConsultarMapaDeRenacimientoPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarCompuertaDeRocasPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.ObjetivoDeNoventaDias;
import com.renaser.os.rag.application.services.herramientas.CompuertaParaProponer;
import com.renaser.os.rag.application.services.herramientas.ConsultarMiMapaHerramienta;
import com.renaser.os.rag.application.services.herramientas.ConsultarRocasHerramienta;
import com.renaser.os.rag.domain.model.diagnostico.DiagnosticoDeLaPersona;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona;
import com.renaser.os.rag.domain.model.mapa.ResumenDelMapa;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Spec 038 («SER estara para las 3»: Escuela, Diplomado y Certificacion). Una sola prueba parametrizada, con una persona de la Certificacion de
 * 90 dias, del Diplomado de 3 meses y del Diplomado de 1 año, que fija lo que SER dice y a donde manda:
 *
 * <ul>
 *   <li>la duracion de SU programa en la situacion, y el nombre de su objetivo grande («90 dias», «3 meses», «1 año»);</li>
 *   <li>los objetivos con ese plazo, tambien cuando faltan (la compuerta, la linea de la situacion y {@code consultar_rocas});</li>
 *   <li>el prompt no nombra ninguna formacion ni supone una duracion fija, y manda a Training (habitos) y a Plan (objetivos);</li>
 *   <li>nunca invita a hacer el Mapa de Renacimiento (retirado), a ninguna de las tres.</li>
 * </ul>
 *
 * Las categorias de habitos de cada formacion se prueban en {@code PropuestaDeCrearHabitoPersonalTest} y en
 * {@code ConsultarAgendaHabitosAdapterTest}, tambien una por formacion.
 */
class SerEnLasTresFormacionesTest {

    private static final UserId PERSONA = UserId.of(UUID.randomUUID());
    private static final LocalDate HOY = LocalDate.of(2026, 11, 3);

    static Stream<Arguments> formaciones() {
        return Stream.of(
                Arguments.of("Certificacion de 90 dias", Formacion.CERTIFICACION, 90, "90 días", null),
                Arguments.of("Diplomado de 3 meses", Formacion.DIPLOMADO, 90, "3 meses", "objetivo de 3 meses"),
                Arguments.of("Diplomado de 1 año", Formacion.DIPLOMADO, 365, "1 año", "objetivo de 1 año"));
    }

    private static SituacionDelAprendiz situacion(Formacion formacion, int dias) {
        return new SituacionDelAprendiz(12, 2, HOY).conDuracion(dias).conFormacion(formacion);
    }

    // spec 038 FR-003
    @ParameterizedTest(name = "{0}")
    @MethodSource("formaciones")
    @DisplayName("spec 038: la situacion dice la duracion de SU programa y nombra el objetivo grande con su plazo")
    void laSituacionDiceSuDuracion(String nombre, Formacion formacion, int dias, String plazo, String objetivoNombrado) {
        String linea = GoogleGenAiRenasiaChatAdapter.formatearSituacion(situacion(formacion, dias));

        assertThat(linea).as(nombre).contains("su dia 12 de " + dias + ", en la fase 2 de 4.");
        if (objetivoNombrado == null) {
            assertThat(linea).as(nombre).doesNotContain("Su objetivo grande se llama");
        } else {
            assertThat(linea).as(nombre).contains("Su objetivo grande se llama \"" + objetivoNombrado + "\"")
                    .contains("nunca como \"objetivo de 90 dias\"");
        }
        assertThat(linea).as(nombre).doesNotContain("Escuela").doesNotContain("Diplomado").doesNotContain("Certificacion");
    }

    // spec 038 FR-002, FR-003
    @ParameterizedTest(name = "{0}")
    @MethodSource("formaciones")
    @DisplayName("spec 038: los objetivos se dicen con el plazo de su formacion, con y sin objetivos, y sin objetivos manda a Plan (nunca al Mapa)")
    void losObjetivosConSuPlazo(String nombre, Formacion formacion, int dias, String plazo, String objetivoNombrado) {
        ConsultarSituacionDelAprendizPort situacionPort = persona -> Optional.of(situacion(formacion, dias));
        ConsultarRocasDelAprendizPort rocas = mock(ConsultarRocasDelAprendizPort.class);
        ConsultarRocasHerramienta consultarRocas = new ConsultarRocasHerramienta(rocas, situacionPort, persona -> DiagnosticoDeLaPersona.ninguno());
        InvocacionHerramienta noventa = new InvocacionHerramienta(ConsultarRocasHerramienta.NOMBRE, Map.of("alcance", "noventa"));

        when(rocas.objetivosDeNoventaDias(PERSONA)).thenReturn(List.of(
                new ObjetivoDeNoventaDias("CUERPO", "Bajar de peso", null, null, null, false, null, null, true)));
        String conObjetivos = ((ResultadoHerramienta.Exito) consultarRocas.ejecutar(PERSONA, noventa)).contenido();
        assertThat(conObjetivos).as(nombre).startsWith("Objetivos de " + plazo + ", por eje:").contains("POR CONFIRMAR en Plan");

        when(rocas.objetivosDeNoventaDias(PERSONA)).thenReturn(List.of());
        String sinObjetivos = ((ResultadoHerramienta.Exito) consultarRocas.ejecutar(PERSONA, noventa)).contenido();
        assertThat(sinObjetivos).as(nombre).startsWith("Todavia no tiene objetivos de " + plazo + ".")
                .contains("pestana Plan").contains("No la mandes al Mapa");

        // La compuerta (antes de proponer un plan) y la linea de la situacion dicen el mismo plazo y la misma salida.
        ConsultarCompuertaDeRocasPort sinRocas = mock(ConsultarCompuertaDeRocasPort.class);
        when(sinRocas.rocasMaestrasCompletas(PERSONA)).thenReturn(false);
        String compuerta = new CompuertaParaProponer(sinRocas, situacionPort, persona -> DiagnosticoDeLaPersona.ninguno()).bloqueoDeObjetivos(PERSONA).orElseThrow();
        assertThat(compuerta).as(nombre).contains("sus tres objetivos de " + plazo).contains("pestaña Plan").doesNotContain("Mapa");

        String linea = GoogleGenAiRenasiaChatAdapter.formatearSituacion(
                situacion(formacion, dias).conMapa(ResumenDelMapa.de(MapaDeLaPersona.sinMapa(), 12).conRocasMaestras(false)));
        assertThat(linea).as(nombre).contains("Objetivos de " + plazo + ": todavia no los tiene escritos ni confirmados")
                .contains("abrir la pestana Plan").doesNotContain("Mapa");
    }

    // spec 038 SC-001
    @ParameterizedTest(name = "{0}")
    @MethodSource("formaciones")
    @DisplayName("spec 038: sin Mapa (retirado) SER no la invita a hacerlo y la manda al diagnostico o a Plan, en cualquiera de las tres")
    void nuncaInvitaAlMapa(String nombre, Formacion formacion, int dias, String plazo, String objetivoNombrado) {
        ConsultarMapaDeRenacimientoPort sinMapa = persona -> MapaDeLaPersona.sinMapa();
        ConsultarSituacionDelAprendizPort situacionPort = persona -> Optional.of(situacion(formacion, dias));

        String texto = ((ResultadoHerramienta.Exito) new ConsultarMiMapaHerramienta(sinMapa, situacionPort).ejecutar(PERSONA,
                InvocacionHerramienta.sinArgumentos(ConsultarMiMapaHerramienta.NOMBRE))).contenido();

        assertThat(texto).as(nombre).contains("NO la invites a hacerlo").contains("consultar_mi_diagnostico").contains("pestana Plan")
                .doesNotContain("invitala a completar").doesNotContain("se arma el dia 7");
    }

    // spec 038 FR-003, FR-004, SC-004
    @ParameterizedTest(name = "{0}")
    @MethodSource("formaciones")
    @DisplayName("spec 038: el prompt no nombra ninguna formacion ni una duracion fija, y manda a Training (habitos) y a Plan (objetivos)")
    void elPromptEsDeLasTres(String nombre, Formacion formacion, int dias, String plazo, String objetivoNombrado) {
        String situacion = GoogleGenAiRenasiaChatAdapter.formatearSituacion(situacion(formacion, dias));
        String prompt = new PromptTemplate(new ClassPathResource(GoogleGenAiRenasiaChatAdapter.RECURSO_PROMPT_ACOMPANANTE))
                .render(Map.of("contexto", "(vacio)", "situacion", situacion, "ambito", GoogleGenAiRenasiaChatAdapter.SIN_AMBITO));

        // Nada de una formacion ni de una duracion fija: el prompt es el mismo para las tres.
        assertThat(prompt).as(nombre).doesNotContain("Escuela").doesNotContain("Diplomado").doesNotContain("Certificacion")
                .doesNotContain("los 90 dias").doesNotContain("programa de 90 dias").doesNotContain("objetivos de 90 dias")
                .doesNotContain("Classroom").contains("Cursos (en Comunidad)");
        assertThat(prompt).as(nombre).contains("su dia 12 de " + dias);
        // Al lugar correcto: los habitos estan en Training, los objetivos y "Cierra tu dia" en Plan.
        assertThat(prompt).as(nombre).contains("- **Training**: sus habitos del dia con su hora.")
                .contains("Los habitos NO estan en\n  Plan ni en Hoy.")
                .contains("- **Plan**: sus objetivos")
                .contains("\"Cierra tu dia\" (opcional, no da puntos)")
                .contains("Eso lo armas en Plan, en el eje que quieras")
                .contains("desde la pestana Training, tocando el habito")
                .contains("dile siempre Negocio");
        // Nunca manda al Mapa.
        assertThat(prompt).as(nombre).doesNotContain("Ir al Mapa").doesNotContain("completarlo en Plan")
                .contains("nunca mandes a nadie a\nhacerlo");
    }
}
