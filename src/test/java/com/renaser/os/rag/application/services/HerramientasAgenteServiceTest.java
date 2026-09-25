package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import com.renaser.os.rag.application.services.herramientas.PropuestaDeMarcarHabito;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.herramienta.CatalogoHerramientasAgente;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Las tres herramientas del agente, ejecutadas de verdad contra los puertos de negocio — sin
 * ningun modelo de por medio, que es exactamente lo que pedia el encargo ("probalas contra el
 * adaptador falso").
 *
 * <p>La mitad de estos casos son fallos, y no por completismo: un modelo pide herramientas que no
 * existen, omite argumentos e inventa identificadores todo el tiempo. Que cada uno de esos vuelva
 * como un {@code Fallo} con un motivo legible — y no como una excepcion que corta el stream — es
 * la parte del contrato que de verdad se rompe si alguien la toca sin darse cuenta.
 */
@ExtendWith(MockitoExtension.class)
class HerramientasAgenteServiceTest {

    /** 9 horas antes del plazo de los habitos de prueba (2026-09-06T05:00:00Z). */
    private static final com.renaser.os.shared.domain.Clock RELOJ = com.renaser.os.shared.domain.FixedClock.at(java.time.Instant.parse("2026-09-05T20:00:00Z"));

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final UUID REGISTRO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private ConsultarAgendaHabitosPort agendaHabitosPort;

    /** Nunca se llama: todo este archivo corre con el flag de la fase 2 apagado (D-153). */
    @Mock
    private ProponerAccionUseCase proponerAccion;

    /**
     * La razon de ser de la bandera: el agente no puede subir la evidencia —por el chat no entran
     * archivos— asi que lo unico que puede hacer con este dato es nombrarlo. Sin el, marcaba el
     * habito como hecho en silencio y la persona se enteraba dias despues por un aviso al mentor
     * de evidencia vencida que nadie le habia pedido.
     */
    @Test
    @DisplayName("Un habito que pide evidencia llega marcado en la linea que lee el modelo")
    void elQuePideEvidenciaSeNombra() {
        when(agendaHabitosPort.deHoyDe(APRENDIZ)).thenReturn(List.of(habitoConEvidencia("Jugo verde", 6)));

        String salida = contenidoDe(servicio().ejecutar(APRENDIZ, new InvocacionHerramienta(
                CatalogoHerramientasAgente.CONSULTAR_HABITOS_DEL_DIA, Map.of())));

        assertThat(salida).contains("exige_evidencia=si");
    }

    /**
     * Y solo cuando es cierto. Repetir `exige_evidencia=no` en cada habito gastaria contexto en
     * decir lo normal, y el modelo parafrasea lo que ve: con la marca presente solo donde
     * corresponde, mencionarla es leer y no razonar.
     */
    @Test
    @DisplayName("El que NO pide evidencia no arrastra ninguna marca")
    void elQueNoPideEvidenciaNoDiceNada() {
        when(agendaHabitosPort.deHoyDe(APRENDIZ)).thenReturn(List.of(habitoVivo("Escritura libre", 6)));

        String salida = contenidoDe(servicio().ejecutar(APRENDIZ, new InvocacionHerramienta(
                CatalogoHerramientasAgente.CONSULTAR_HABITOS_DEL_DIA, Map.of())));

        assertThat(salida).doesNotContain("exige_evidencia");
    }

    /** Pedir evidencia no cambia lo que paga ni si se puede entregar: son ejes distintos. */
    @Test
    @DisplayName("Exigir evidencia no altera los puntos en juego del habito")
    void exigirEvidenciaNoTocaLosPuntos() {
        when(agendaHabitosPort.deHoyDe(APRENDIZ)).thenReturn(List.of(habitoConEvidencia("Jugo verde", 6)));

        String salida = contenidoDe(servicio().ejecutar(APRENDIZ, new InvocacionHerramienta(
                CatalogoHerramientasAgente.CONSULTAR_HABITOS_DEL_DIA, Map.of())));

        assertThat(salida).contains("puntos_en_juego=6 de 10")
                .contains("Total en juego: 6 puntos en 1 habito(s)");
    }

    private HerramientasAgenteService servicio() {
        return new HerramientasAgenteService(agendaHabitosPort, List.of(),
                new PropuestaDeMarcarHabito(agendaHabitosPort, proponerAccion, false), RELOJ);
    }

    private static HabitoDelDia habitoVivo(String titulo, int puntos) {
        return new HabitoDelDia(REGISTRO, titulo, "PENDIENTE", puntos, 10,
                Instant.parse("2026-09-06T05:00:00Z"), false);
    }

    /** Uno que ademas pide foto: el agente no puede subirla, solo nombrarla. */
    private static HabitoDelDia habitoConEvidencia(String titulo, int puntos) {
        return new HabitoDelDia(REGISTRO, titulo, "PENDIENTE", puntos, 10,
                Instant.parse("2026-09-06T05:00:00Z"), true);
    }

    private static HabitoDelDia habitoCompletado(String titulo) {
        return new HabitoDelDia(UUID.randomUUID(), titulo, "COMPLETADO", null, null, null, false);
    }

    private static String contenidoDe(ResultadoHerramienta resultado) {
        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        return ((ResultadoHerramienta.Exito) resultado).contenido();
    }

    private static String motivoDe(ResultadoHerramienta resultado) {
        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        return ((ResultadoHerramienta.Fallo) resultado).motivo();
    }

    @Test
    @DisplayName("con el flag apagado, marcar_habito_completado conserva la descripcion de siempre")
    void conElFlagApagadoLaDescripcionNoCambia() {
        assertThat(servicio().disponibles(AgenteConversacional.COMPANION))
                .isEqualTo(CatalogoHerramientasAgente.definiciones());
    }

    @Test
    @DisplayName("el acompanante tiene las tres herramientas; Sparkie no tiene ninguna (D-102)")
    void soloElAcompananteTieneHerramientas() {
        assertThat(servicio().disponibles(AgenteConversacional.COMPANION))
                .extracting(com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta::nombre)
                .containsExactly(CatalogoHerramientasAgente.CONSULTAR_HABITOS_DEL_DIA,
                        CatalogoHerramientasAgente.CONSULTAR_PUNTOS_EN_JUEGO,
                        CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO);
        assertThat(servicio().disponibles(AgenteConversacional.COURSE_TUTOR)).isEmpty();
    }

    @Test
    @DisplayName("consultar_habitos_del_dia devuelve id, titulo, estado, puntos en juego y vencimiento")
    void consultarHabitosDelDia() {
        when(agendaHabitosPort.deHoyDe(APRENDIZ)).thenReturn(List.of(habitoVivo("Meditacion", 10)));

        String texto = contenidoDe(servicio().ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(CatalogoHerramientasAgente.CONSULTAR_HABITOS_DEL_DIA)));

        assertThat(texto).contains(REGISTRO.toString()).contains("Meditacion").contains("estado=PENDIENTE")
                .contains("puntos_en_juego=10 de 10").contains("vence_en=9 h 0 min")
                // El total viaja en la misma respuesta para que el modelo no encadene una
                // segunda herramienta (un viaje mas a Gemini) para sumar lo que ya tiene.
                .contains("Total en juego: 10 puntos en 1 habito(s)");
    }

    @Test
    @DisplayName("sin habitos generados lo dice, en vez de devolver una lista vacia que el modelo tenga que adivinar")
    void consultarHabitosSinNada() {
        when(agendaHabitosPort.deHoyDe(APRENDIZ)).thenReturn(List.of());

        assertThat(contenidoDe(servicio().ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(CatalogoHerramientasAgente.CONSULTAR_HABITOS_DEL_DIA))))
                .contains("no tiene ningun habito");
    }

    @Test
    @DisplayName("consultar_puntos_en_juego suma solo los habitos que todavia se pueden entregar")
    void puntosEnJuegoSumaSoloLosVivos() {
        when(agendaHabitosPort.deHoyDe(APRENDIZ)).thenReturn(List.of(habitoVivo("Meditacion", 10),
                habitoVivo("Jugo verde", 6), habitoCompletado("Despertar")));

        assertThat(contenidoDe(servicio().ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(CatalogoHerramientasAgente.CONSULTAR_PUNTOS_EN_JUEGO))))
                .contains("16 puntos en juego").contains("2 habito(s)");
    }

    @Test
    @DisplayName("marcar_habito_completado delega en el caso de uso real y devuelve los puntos otorgados")
    void marcarCompletado() {
        when(agendaHabitosPort.completar(APRENDIZ, REGISTRO)).thenReturn(8);

        String texto = contenidoDe(servicio().ejecutar(APRENDIZ, new InvocacionHerramienta(
                CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO,
                Map.of(CatalogoHerramientasAgente.ARGUMENTO_REGISTRO_ID, REGISTRO.toString()))));

        assertThat(texto).isEqualTo("Habito marcado como completado. Puntos otorgados: 8.");
        verify(agendaHabitosPort).completar(APRENDIZ, REGISTRO);
        verify(proponerAccion, never()).proponer(any(), any(), any());
    }

    @Test
    @DisplayName("una herramienta inventada por el modelo es un Fallo legible, no una excepcion")
    void herramientaInexistente() {
        assertThat(motivoDe(servicio().ejecutar(APRENDIZ, InvocacionHerramienta.sinArgumentos("borrar_todo"))))
                .contains("No existe una herramienta");
        verify(agendaHabitosPort, never()).deHoyDe(any());
    }

    @Test
    @DisplayName("si falta el argumento obligatorio se avisa cual, sin tocar ningun puerto")
    void faltaElArgumentoObligatorio() {
        assertThat(motivoDe(servicio().ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO))))
                .contains("Faltan datos").contains(CatalogoHerramientasAgente.ARGUMENTO_REGISTRO_ID);
        verify(agendaHabitosPort, never()).completar(any(), any());
    }

    @Test
    @DisplayName("un identificador inventado no llega al caso de uso: se rechaza antes")
    void identificadorInvalido() {
        assertThat(motivoDe(servicio().ejecutar(APRENDIZ, new InvocacionHerramienta(
                CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO,
                Map.of(CatalogoHerramientasAgente.ARGUMENTO_REGISTRO_ID, "el-habito-de-la-manana")))))
                .contains("no es valido");
        verify(agendaHabitosPort, never()).completar(any(), any());
    }

    @Test
    @DisplayName("si el negocio rechaza la completacion, el modelo recibe un motivo apto para repetir")
    void fallaDeNegocioAlCompletar() {
        when(agendaHabitosPort.completar(APRENDIZ, REGISTRO))
                .thenThrow(new IllegalStateException("El habito expiro"));

        String motivo = motivoDe(servicio().ejecutar(APRENDIZ, new InvocacionHerramienta(
                CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO,
                Map.of(CatalogoHerramientasAgente.ARGUMENTO_REGISTRO_ID, REGISTRO.toString()))));

        assertThat(motivo).contains("No se pudo marcar ese habito")
                // el detalle tecnico va al log, nunca al texto que el asistente le repite a la persona
                .doesNotContain("IllegalStateException");
    }

    @Test
    @DisplayName("bateria 2026-09-25: un habito vencido se marca ya_vencio y no cuenta como pendiente ni en juego")
    void vencidoNoCuenta() {
        var agenda = mock(ConsultarAgendaHabitosPort.class);
        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(new HabitoDelDia(UUID.randomUUID(), "Ritual", "PENDIENTE",
                0, 10, Instant.parse("2026-09-05T14:10:00Z"), false)));
        var servicio = new HerramientasAgenteService(agenda, List.of(),
                new PropuestaDeMarcarHabito(agenda, proponerAccion, false), RELOJ);

        String contenido = ((ResultadoHerramienta.Exito) servicio.ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(CatalogoHerramientasAgente.CONSULTAR_HABITOS_DEL_DIA))).contenido();

        assertThat(contenido).contains("ya_vencio=si").doesNotContain("vence_en")
                .contains("Total en juego: 0 puntos en 0 habito(s)").contains("1 ya vencieron hoy");
    }
}
