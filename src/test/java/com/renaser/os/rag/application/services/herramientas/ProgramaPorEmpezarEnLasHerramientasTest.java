package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase.PropuestaCreada;
import com.renaser.os.rag.application.ports.out.diagnostico.ConsultarDiagnosticoDeLaPersonaPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.PlanDelAprendiz;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarCompuertaDeRocasPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.PlanDeManana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDelDia;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort;
import com.renaser.os.rag.application.services.InicioDelPrograma;
import com.renaser.os.rag.domain.model.diagnostico.DiagnosticoDeLaPersona;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Issue #65 (seguimiento del #63): una cuenta de la Escuela SIN diagnostico, con el programa que empieza manana, le pedia a SER «Armemos mi plan» y a veces
 * contestaba «tus objetivos estan pendientes… confirma los de tu diagnostico», sin decir cuando empieza y hablando de un diagnostico que no existe.
 *
 * <p>Estas pruebas fijan, sin IA, lo que devuelven las herramientas (el texto que el modelo SI lee antes de contestar): mientras el programa no empieza, el
 * resultado abre con la fecha de inicio y la orden de decirla primero; y los textos de «sin objetivos» nombran el diagnostico solo si la persona lo tiene.
 * Se repiten en la Escuela y en el Diplomado de 3 meses y de 1 año (el plazo es el de SU formacion), y desde el Dia 1 no hay esa linea.
 */
class ProgramaPorEmpezarEnLasHerramientasTest {

    private static final UserId PERSONA = UserId.of(UUID.randomUUID());
    /** Lunes 2 de noviembre de 2026; manana, martes 3. */
    private static final LocalDate HOY = LocalDate.of(2026, 11, 2);
    private static final LocalDate MANANA = HOY.plusDays(1);

    private static final String EMPIEZA_MANANA = "Su programa empieza mañana (martes 3 de noviembre). Todavía no tiene día ni fase. "
            + "Díselo antes de cualquier otra cosa.";

    private static final DiagnosticoDeLaPersona SIN_DIAGNOSTICO = DiagnosticoDeLaPersona.ninguno();
    private static final DiagnosticoDeLaPersona CON_DIAGNOSTICO = new DiagnosticoDeLaPersona(true, true, "CUERPO", List.of());

    private final ConsultarRocasDelAprendizPort rocas = mock(ConsultarRocasDelAprendizPort.class);
    private final PlanificarRocasPort planificar = mock(PlanificarRocasPort.class);
    private final ProponerAccionUseCase proponer = mock(ProponerAccionUseCase.class);
    private final GestionarPlanDeHabitosPort planDeHabitos = mock(GestionarPlanDeHabitosPort.class);
    private final MapaParaProponer sinMapaQueLeer = new MapaParaProponer(
            id -> { throw new IllegalStateException("no se lee en esta prueba"); }, id -> Optional.empty());

    @BeforeEach
    void reglas() {
        when(planificar.ejesValidos()).thenReturn(List.of("CUERPO", "TRABAJO", "RELACIONES"));
        when(rocas.deManana(PERSONA)).thenReturn(new RocasDelDia(MANANA, List.of(), new PlanDeManana(false, 0, true, LocalTime.of(18, 0), true)));
        when(rocas.deHoy(PERSONA)).thenReturn(new RocasDelDia(HOY, List.of(), new PlanDeManana(false, 0, true, LocalTime.of(18, 0), true)));
        when(planDeHabitos.planDe(PERSONA)).thenReturn(new PlanDelAprendiz(HOY, List.of(), List.of()));
        when(proponer.proponer(any(), any(), anyString())).thenReturn(new PropuestaCreada(UUID.randomUUID(), "r", Instant.parse("2026-11-03T05:00:00Z")));
    }

    static Stream<Arguments> formaciones() {
        return Stream.of(
                Arguments.of("Escuela de 90 dias", Formacion.ESCUELA, 90, "90 días"),
                Arguments.of("Diplomado de 3 meses", Formacion.DIPLOMADO, 90, "3 meses"),
                Arguments.of("Diplomado de 1 año", Formacion.DIPLOMADO, 365, "1 año"));
    }

    /** Dia 0: activo su programa y empieza manana. */
    private static SituacionDelAprendiz diaCero(Formacion formacion, int dias) {
        return new SituacionDelAprendiz(0, 1, HOY).conDuracion(dias).conFormacion(formacion).conDiaUno(MANANA);
    }

    /** Dia 1: su programa ya empezo. */
    private static SituacionDelAprendiz diaUno(Formacion formacion, int dias) {
        return new SituacionDelAprendiz(1, 1, MANANA).conDuracion(dias).conFormacion(formacion).conDiaUno(MANANA);
    }

    private static CompuertaParaProponer compuerta(boolean conObjetivos, SituacionDelAprendiz situacion, DiagnosticoDeLaPersona diagnostico) {
        return new CompuertaParaProponer(new ConsultarCompuertaDeRocasPort() {
            @Override
            public boolean rocasMaestrasCompletas(UserId aprendizId) {
                return conObjetivos;
            }

            @Override
            public List<String> ejesSinObjetivoSemanal(UserId aprendizId, LocalDate fecha) {
                return List.of();
            }
        }, persona -> Optional.of(situacion), persona -> diagnostico);
    }

    private ResultadoHerramienta planDelDia(CompuertaParaProponer compuerta) {
        return new ProponerPlanDelDiaHerramienta(rocas, planificar, proponer, sinMapaQueLeer, compuerta).ejecutar(PERSONA,
                new InvocacionHerramienta(ProponerPlanDelDiaHerramienta.NOMBRE,
                        Map.of("plan", "{\"acciones\":[{\"eje\":\"CUERPO\",\"titulo\":\"Caminar 30 minutos\"}]}")));
    }

    private ResultadoHerramienta agregarAccion(CompuertaParaProponer compuerta) {
        return new ProponerAgregarAccionHerramienta(rocas, planificar, proponer, planDeHabitos, sinMapaQueLeer, compuerta).ejecutar(PERSONA,
                new InvocacionHerramienta(ProponerAgregarAccionHerramienta.NOMBRE, Map.of("eje", "CUERPO", "titulo", "Correr 5 km")));
    }

    private ResultadoHerramienta planDeLaSemana(CompuertaParaProponer compuerta) {
        return new ProponerPlanDeLaSemanaHerramienta(planificar, proponer, sinMapaQueLeer, compuerta).ejecutar(PERSONA,
                new InvocacionHerramienta(ProponerPlanDeLaSemanaHerramienta.NOMBRE,
                        Map.of("plan", "{\"objetivos\":[{\"eje\":\"CUERPO\",\"titulo\":\"Bajar 1 kg\"}]}")));
    }

    private ConsultarRocasHerramienta consultarRocas(SituacionDelAprendiz situacion, DiagnosticoDeLaPersona diagnostico) {
        ConsultarSituacionDelAprendizPort puerto = persona -> Optional.of(situacion);
        ConsultarDiagnosticoDeLaPersonaPort diagnosticoPort = persona -> diagnostico;
        return new ConsultarRocasHerramienta(rocas, puerto, diagnosticoPort);
    }

    private static InvocacionHerramienta alcance(String alcance) {
        return new InvocacionHerramienta(ConsultarRocasHerramienta.NOMBRE, Map.of("alcance", alcance));
    }

    private static String texto(ResultadoHerramienta resultado) {
        return switch (resultado) {
            case ResultadoHerramienta.Exito exito -> exito.contenido();
            case ResultadoHerramienta.Fallo fallo -> fallo.motivo();
        };
    }

    // ---------------------------------------------------------------------------------------------------------------------------- consultar_rocas

    // issue #65
    @ParameterizedTest(name = "{0}")
    @MethodSource("formaciones")
    @DisplayName("issue #65: consultar_rocas sin diagnostico en el dia 0 abre con la fecha de inicio y manda a escribirlos en Plan, sin nombrar un diagnostico")
    void consultarObjetivosSinDiagnosticoEnElDiaCero(String nombre, Formacion formacion, int dias, String plazo) {
        when(rocas.objetivosDeNoventaDias(PERSONA)).thenReturn(List.of());

        String texto = texto(consultarRocas(diaCero(formacion, dias), SIN_DIAGNOSTICO).ejecutar(PERSONA, alcance("noventa")));

        assertThat(texto).as(nombre).startsWith(EMPIEZA_MANANA + "\nTodavia no tiene objetivos de " + plazo + ".")
                .contains("Los escribe ella misma en la pestana Plan (\"Escribelos en Plan\")").contains("llevala a Plan")
                .doesNotContainIgnoringCase("diagnostic");
    }

    // issue #65
    @ParameterizedTest(name = "{0}")
    @MethodSource("formaciones")
    @DisplayName("issue #65: consultar_rocas con diagnostico terminado en el dia 0 abre con la fecha y dice que nacen de su diagnostico, como hasta ahora")
    void consultarObjetivosConDiagnosticoEnElDiaCero(String nombre, Formacion formacion, int dias, String plazo) {
        when(rocas.objetivosDeNoventaDias(PERSONA)).thenReturn(List.of());

        String texto = texto(consultarRocas(diaCero(formacion, dias), CON_DIAGNOSTICO).ejecutar(PERSONA, alcance("noventa")));

        assertThat(texto).as(nombre).startsWith(EMPIEZA_MANANA + "\nTodavia no tiene objetivos de " + plazo + ". Nacen de su diagnostico ")
                .contains("pestana Plan");
    }

    // issue #65
    @Test
    @DisplayName("issue #65: todos los alcances de consultar_rocas abren con la fecha de inicio mientras el programa no empieza, y un fallo tambien")
    void todosLosAlcancesDicenCuandoEmpieza() {
        when(rocas.objetivosDeNoventaDias(PERSONA)).thenReturn(List.of());
        ConsultarRocasHerramienta herramienta = consultarRocas(diaCero(Formacion.ESCUELA, 90), SIN_DIAGNOSTICO);

        for (String alcance : List.of("hoy", "manana", "mes", "noventa")) {
            ResultadoHerramienta resultado = herramienta.ejecutar(PERSONA, alcance(alcance));
            assertThat(texto(resultado)).as(alcance).startsWith(EMPIEZA_MANANA + "\n");
        }
        when(rocas.deHoy(PERSONA)).thenThrow(new NoSuchElementException("sin programa"));
        ResultadoHerramienta sinPrograma = herramienta.ejecutar(PERSONA, alcance("hoy"));
        assertThat(sinPrograma).isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(texto(sinPrograma)).isEqualTo(EMPIEZA_MANANA + "\nNo encontre un programa activo para esta cuenta.");
    }

    // issue #65
    @ParameterizedTest(name = "{0}")
    @MethodSource("formaciones")
    @DisplayName("issue #65: desde el dia 1 consultar_rocas no lleva la linea de inicio")
    void desdeElDiaUnoNoHayLineaDeInicio(String nombre, Formacion formacion, int dias, String plazo) {
        when(rocas.objetivosDeNoventaDias(PERSONA)).thenReturn(List.of());

        for (String alcance : List.of("hoy", "noventa")) {
            String texto = texto(consultarRocas(diaUno(formacion, dias), SIN_DIAGNOSTICO).ejecutar(PERSONA, alcance(alcance)));
            assertThat(texto).as(nombre + " " + alcance).doesNotContain("Su programa empieza").doesNotContain("Díselo antes");
        }
    }

    // issue #65
    @Test
    @DisplayName("issue #65: un diagnostico asignado pero sin terminar, o que no se puede leer, no se nombra: solo «escribelos en Plan»")
    void sinTerminarOSinLeerElDiagnosticoNoSeNombra() {
        when(rocas.objetivosDeNoventaDias(PERSONA)).thenReturn(List.of());
        DiagnosticoDeLaPersona sinTerminar = new DiagnosticoDeLaPersona(true, false, null, List.of());
        ConsultarDiagnosticoDeLaPersonaPort quePeta = persona -> { throw new IllegalStateException("base caida"); };

        assertThat(texto(consultarRocas(diaCero(Formacion.ESCUELA, 90), sinTerminar).ejecutar(PERSONA, alcance("noventa"))))
                .doesNotContainIgnoringCase("diagnostic").contains("Escribelos en Plan");
        assertThat(texto(new ConsultarRocasHerramienta(rocas, persona -> Optional.of(diaCero(Formacion.ESCUELA, 90)), quePeta)
                .ejecutar(PERSONA, alcance("noventa")))).doesNotContainIgnoringCase("diagnostic").contains("Escribelos en Plan");
    }

    // ---------------------------------------------------------------------------------------------------------------- proponer_* sin objetivos

    // issue #65
    @ParameterizedTest(name = "{0}")
    @MethodSource("formaciones")
    @DisplayName("issue #65: proponer_plan_del_dia y proponer_agregar_accion, sin objetivos y sin diagnostico, abren con la fecha y mandan a escribirlos en Plan")
    void sinObjetivosSinDiagnosticoEnElDiaCero(String nombre, Formacion formacion, int dias, String plazo) {
        CompuertaParaProponer sinObjetivos = compuerta(false, diaCero(formacion, dias), SIN_DIAGNOSTICO);
        String esperado = EMPIEZA_MANANA + "\n" + CompuertaParaProponer.sinObjetivos(plazo, false);

        for (ResultadoHerramienta resultado : List.of(planDelDia(sinObjetivos), agregarAccion(sinObjetivos), planDeLaSemana(sinObjetivos))) {
            assertThat(resultado).as(nombre).isEqualTo(ResultadoHerramienta.fallo(esperado));
        }
        assertThat(esperado).startsWith("Su programa empieza mañana (martes 3 de noviembre).")
                .contains("sus tres objetivos de " + plazo).contains("Escríbelos en Plan").doesNotContainIgnoringCase("diagnóstic");
    }

    // issue #65
    @ParameterizedTest(name = "{0}")
    @MethodSource("formaciones")
    @DisplayName("issue #65: con diagnostico terminado, el motivo sigue diciendo que puede confirmar los que nacieron de el")
    void sinObjetivosConDiagnosticoEnElDiaCero(String nombre, Formacion formacion, int dias, String plazo) {
        CompuertaParaProponer sinObjetivos = compuerta(false, diaCero(formacion, dias), CON_DIAGNOSTICO);
        String esperado = EMPIEZA_MANANA + "\n" + CompuertaParaProponer.sinObjetivos(plazo, true);

        assertThat(planDelDia(sinObjetivos)).as(nombre).isEqualTo(ResultadoHerramienta.fallo(esperado));
        assertThat(agregarAccion(sinObjetivos)).as(nombre).isEqualTo(ResultadoHerramienta.fallo(esperado));
        assertThat(esperado).contains("confirmar los que nacieron de su diagnóstico").contains("sus tres objetivos de " + plazo);
    }

    // issue #65
    @Test
    @DisplayName("issue #65: desde el dia 1 el motivo de «sin objetivos» no lleva la linea de inicio, con o sin diagnostico")
    void desdeElDiaUnoElMotivoNoLlevaLaLineaDeInicio() {
        assertThat(planDelDia(compuerta(false, diaUno(Formacion.ESCUELA, 90), SIN_DIAGNOSTICO)))
                .isEqualTo(ResultadoHerramienta.fallo(CompuertaParaProponer.SIN_OBJETIVOS));
        assertThat(agregarAccion(compuerta(false, diaUno(Formacion.ESCUELA, 90), CON_DIAGNOSTICO)))
                .isEqualTo(ResultadoHerramienta.fallo(CompuertaParaProponer.sinObjetivos("90 días", true)));
    }

    // -------------------------------------------------------------------------------------------------------------- proponer_* con objetivos

    // issue #65
    @ParameterizedTest(name = "{0}")
    @MethodSource("formaciones")
    @DisplayName("issue #65: con objetivos escritos, la propuesta del dia 0 tambien abre con la fecha de inicio; desde el dia 1 no")
    void laPropuestaDelDiaCeroDiceCuandoEmpieza(String nombre, Formacion formacion, int dias, String plazo) {
        CompuertaParaProponer enElDiaCero = compuerta(true, diaCero(formacion, dias), SIN_DIAGNOSTICO);
        CompuertaParaProponer enElDiaUno = compuerta(true, diaUno(formacion, dias), SIN_DIAGNOSTICO);

        for (ResultadoHerramienta resultado : List.of(planDelDia(enElDiaCero), agregarAccion(enElDiaCero), planDeLaSemana(enElDiaCero))) {
            assertThat(resultado).as(nombre).isInstanceOf(ResultadoHerramienta.Exito.class);
            assertThat(texto(resultado)).as(nombre).startsWith(EMPIEZA_MANANA + "\nPropuesta creada: ");
        }
        for (ResultadoHerramienta resultado : List.of(planDelDia(enElDiaUno), agregarAccion(enElDiaUno), planDeLaSemana(enElDiaUno))) {
            assertThat(texto(resultado)).as(nombre).startsWith("Propuesta creada: ").doesNotContain("Su programa empieza");
        }
    }

    // issue #65
    @Test
    @DisplayName("issue #65: sin objetivo semanal en el dia 0, el motivo tambien abre con la fecha de inicio")
    void sinObjetivoSemanalEnElDiaCero() {
        CompuertaParaProponer sinSemanal = new CompuertaParaProponer(new ConsultarCompuertaDeRocasPort() {
            @Override
            public boolean rocasMaestrasCompletas(UserId aprendizId) {
                return true;
            }

            @Override
            public List<String> ejesSinObjetivoSemanal(UserId aprendizId, LocalDate fecha) {
                return List.of("CUERPO");
            }
        }, persona -> Optional.of(diaCero(Formacion.ESCUELA, 90)), persona -> SIN_DIAGNOSTICO);

        assertThat(planDelDia(sinSemanal)).isEqualTo(ResultadoHerramienta.fallo(
                EMPIEZA_MANANA + "\n" + CompuertaParaProponer.sinObjetivoSemanal(List.of("CUERPO"), MANANA)));
    }

    // issue #65
    @Test
    @DisplayName("issue #65: sin poder leer la situacion, o sin haber activado el programa (no hay fecha), las herramientas salen sin la linea y sin romperse")
    void sinSituacionOSinActivarNoHayLinea() {
        CompuertaParaProponer sinLeer = new CompuertaParaProponer(new ConsultarCompuertaDeRocasPort() {
            @Override
            public boolean rocasMaestrasCompletas(UserId aprendizId) {
                return false;
            }

            @Override
            public List<String> ejesSinObjetivoSemanal(UserId aprendizId, LocalDate fecha) {
                return List.of();
            }
        }, persona -> { throw new IllegalStateException("base caida"); }, persona -> SIN_DIAGNOSTICO);

        assertThat(sinLeer.bloqueoDeObjetivos(PERSONA)).contains(CompuertaParaProponer.SIN_OBJETIVOS);
        assertThat(sinLeer.inicioDelPrograma(PERSONA)).isEmpty();
        SituacionDelAprendiz sinActivar = new SituacionDelAprendiz(0, 1, HOY);
        assertThat(compuerta(false, sinActivar, SIN_DIAGNOSTICO).inicioDelPrograma(PERSONA)).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------------------------------- InicioDelPrograma

    // issue #65
    @Test
    @DisplayName("issue #65: InicioDelPrograma dice manana, hoy o la fecha, y nada si ya empezo o si no activo su programa")
    void inicioDelPrograma() {
        assertThat(InicioDelPrograma.de(diaCero(Formacion.ESCUELA, 90)).orElseThrow().paraLasHerramientas()).isEqualTo(EMPIEZA_MANANA);
        assertThat(InicioDelPrograma.de(diaCero(Formacion.ESCUELA, 90)).orElseThrow().cuandoSinEnie()).isEqualTo("manana (martes 3 de noviembre)");
        assertThat(InicioDelPrograma.de(new SituacionDelAprendiz(0, 1, HOY).conDiaUno(HOY)).orElseThrow().paraLasHerramientas())
                .startsWith("Su programa empieza hoy (lunes 2 de noviembre).");
        assertThat(InicioDelPrograma.de(new SituacionDelAprendiz(0, 1, HOY).conDiaUno(HOY.plusDays(3))).orElseThrow().paraLasHerramientas())
                .startsWith("Su programa empieza el jueves 5 de noviembre.");
        assertThat(InicioDelPrograma.de(new SituacionDelAprendiz(0, 1, HOY))).as("sin Dia 1 no hay fecha").isEmpty();
        assertThat(InicioDelPrograma.de(diaUno(Formacion.ESCUELA, 90))).as("ya empezo").isEmpty();
        assertThat(InicioDelPrograma.de(null)).isEmpty();
    }
}
