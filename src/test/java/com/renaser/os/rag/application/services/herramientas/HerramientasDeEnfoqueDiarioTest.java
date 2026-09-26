package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort;
import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort.AudioDeHoy;
import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort.DiaSinCelularDeHoy;
import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort.EspirituDeHoy;
import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort.EstadoEspiritu;
import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort.SantuarioDeHoy;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las cuatro herramientas de foco del dia (Espiritu, Santuario, Dia sin celular) y sus
 * {@code AccionConfirmable}. Las de escritura proponen, nunca escriben; las confirmables escriben y
 * traducen el rechazo del negocio. Regla 02: el reloj es 03:00 UTC, las 22:00 del dia ANTERIOR en Lima.
 */
class HerramientasDeEnfoqueDiarioTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** 22:00 del miercoles 23/09 en Lima. */
    private static final Instant NOCHE_EN_LIMA = Instant.parse("2026-09-24T03:00:00Z");
    /** 10:30 del miercoles en Lima. */
    private static final Instant MANANA_EN_LIMA = Instant.parse("2026-09-23T15:30:00Z");
    private static final Instant MEDIODIA_EN_LIMA = Instant.parse("2026-09-23T17:00:00Z");
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 9, 23);
    private static final UUID REGISTRO = UUID.fromString("77777777-7777-7777-7777-777777777777");

    private final EnfoqueDiarioDelAprendizPort enfoque = mock(EnfoqueDiarioDelAprendizPort.class);
    private final ProponerAccionUseCase proponerAccion = mock(ProponerAccionUseCase.class);

    private static EspirituDeHoy espiritu(EstadoEspiritu estado, Instant entregadoEn, Integer puntos) {
        boolean sinAudio = estado == EstadoEspiritu.SIN_AUDIO_HOY
                || estado == EstadoEspiritu.ANTES_DE_LA_HORA_DE_DESBLOQUEO;
        AudioDeHoy audio = sinAudio ? null : new AudioDeHoy(5, "Soltar", MEDIODIA_EN_LIMA, entregadoEn);
        return new EspirituDeHoy(LIMA, MIERCOLES, LocalTime.of(7, 0), LocalTime.of(12, 0), estado, audio, puntos);
    }

    private static String exito(ResultadoHerramienta resultado) {
        return ((ResultadoHerramienta.Exito) resultado).contenido();
    }

    private static String fallo(ResultadoHerramienta resultado) {
        return ((ResultadoHerramienta.Fallo) resultado).motivo();
    }

    // ─── consultar_espiritu_de_hoy ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("consultar: a las 03:00 UTC habla del dia y la hora de Lima, y de la hora limite vencida")
    void consultarPendienteDeNoche() {
        when(enfoque.espirituDeHoy(APRENDIZ)).thenReturn(espiritu(EstadoEspiritu.PENDIENTE, null, 0));

        String texto = exito(new ConsultarEspirituDeHoyHerramienta(enfoque, FixedClock.at(NOCHE_EN_LIMA))
                .ejecutar(APRENDIZ, InvocacionHerramienta.sinArgumentos(ConsultarEspirituDeHoyHerramienta.NOMBRE)));

        assertThat(texto).contains("miércoles 23/09").contains("ahora son las 22:00")
                .contains("SIN ENTREGAR").contains("FUERA DE PLAZO").contains("ya sin puntos");
    }

    @Test
    @DisplayName("consultar: pendiente a tiempo dice cuanto falta y los puntos de Pastilla Renacer")
    void consultarPendienteATiempo() {
        when(enfoque.espirituDeHoy(APRENDIZ)).thenReturn(espiritu(EstadoEspiritu.PENDIENTE, null, 10));

        String texto = exito(new ConsultarEspirituDeHoyHerramienta(enfoque, FixedClock.at(MANANA_EN_LIMA))
                .ejecutar(APRENDIZ, InvocacionHerramienta.sinArgumentos(ConsultarEspirituDeHoyHerramienta.NOMBRE)));

        assertThat(texto).contains("vence a las 12:00").contains("faltan 1 h 30 min").contains("+10 puntos");
    }

    @Test
    @DisplayName("consultar: una cuenta suspendida vuelve como Fallo legible, no como excepcion")
    void consultarSuspendida() {
        when(enfoque.espirituDeHoy(APRENDIZ)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        ResultadoHerramienta resultado = new ConsultarEspirituDeHoyHerramienta(enfoque, FixedClock.at(NOCHE_EN_LIMA))
                .ejecutar(APRENDIZ, InvocacionHerramienta.sinArgumentos(ConsultarEspirituDeHoyHerramienta.NOMBRE));

        assertThat(fallo(resultado)).contains("suspendida");
    }

    // ─── proponer_resumen_espiritu ───────────────────────────────────────────────────────────

    private ResultadoHerramienta proponerResumen(Instant ahora, String queSentiste, String queTeLlevas) {
        return new PropuestaDeResumenEspiritu(enfoque, proponerAccion, FixedClock.at(ahora)).ejecutar(APRENDIZ,
                new InvocacionHerramienta(PropuestaDeResumenEspiritu.NOMBRE,
                        Map.of("que_sentiste", queSentiste, "que_te_llevas", queTeLlevas)));
    }

    /**
     * D-171: el texto es el mismo que arma el modal de la app, con sus dos preguntas exactas:
     * {@code preguntas.map((p, i) => `${p}\n${respuesta}`).join('\n\n')}.
     */
    private static final String TEXTO_DE_LA_APP = "¿Qué sentiste después de escuchar este audio?\nPaz, y ganas de "
            + "llorar un rato\n\n¿Qué te llevas de este audio para tu día de hoy?\nSoltar lo que no controlo";

    @Test
    @DisplayName("resumen: arma el texto con las dos preguntas de la app, el dia del audio de hoy y los puntos")
    void proponeResumen() {
        when(enfoque.espirituDeHoy(APRENDIZ)).thenReturn(espiritu(EstadoEspiritu.PENDIENTE, null, 10));

        ResultadoHerramienta resultado = proponerResumen(MANANA_EN_LIMA, "  Paz, y ganas de llorar un rato ",
                "Soltar lo que no controlo  ");

        String resumen = "Enviar tus respuestas de Espiritu del audio 5 'Soltar', a tiempo (vence a las 12:00): \""
                + TEXTO_DE_LA_APP + "\". Tambien marca 'Pastilla Renacer' de hoy como hecha (+10 puntos si lo "
                + "confirmas ahora)";
        // Lo guardado conserva la forma vieja (resumen + dia): la confirmable no cambio.
        verify(proponerAccion).proponer(APRENDIZ, new InvocacionHerramienta(PropuestaDeResumenEspiritu.NOMBRE,
                Map.of(PropuestaDeResumenEspiritu.ARGUMENTO_RESUMEN, TEXTO_DE_LA_APP,
                        PropuestaDeResumenEspiritu.ARGUMENTO_DIA, "5")), resumen);
        verify(enfoque, never()).entregarResumenEspiritu(any(), anyInt(), anyString());
        assertThat(exito(resultado)).contains("TODAVIA NO esta hecho");
    }

    @Test
    @DisplayName("resumen: una respuesta larga pasa entera; sin una de las dos, no propone")
    void respuestasLargasYFaltantes() {
        when(enfoque.espirituDeHoy(APRENDIZ)).thenReturn(espiritu(EstadoEspiritu.PENDIENTE, null, 10));
        String larga = "Senti muchas cosas. ".repeat(80).trim();

        proponerResumen(MANANA_EN_LIMA, larga, "Calma");
        verify(proponerAccion).proponer(any(), org.mockito.ArgumentMatchers.argThat(invocacion -> invocacion
                .argumento(PropuestaDeResumenEspiritu.ARGUMENTO_RESUMEN).contains(larga)), anyString());

        assertThat(fallo(proponerResumen(MANANA_EN_LIMA, "Calma", "  "))).contains("Falta una de las dos respuestas");
    }

    @Test
    @DisplayName("resumen: la herramienta pide las dos respuestas y le dice al modelo las preguntas exactas")
    void definicionConLasDosPreguntas() {
        var definicion = new PropuestaDeResumenEspiritu(enfoque, proponerAccion, FixedClock.at(MANANA_EN_LIMA))
                .definicion();

        assertThat(definicion.parametros()).extracting(p -> p.nombre())
                .containsExactly("que_sentiste", "que_te_llevas");
        assertThat(definicion.descripcion()).contains("¿Qué sentiste después de escuchar este audio?")
                .contains("¿Qué te llevas de este audio para tu día de hoy?").contains("sin cortarla");
    }

    @Test
    @DisplayName("resumen: si ya lo envio, no propone")
    void noProponeSiYaSeEnvio() {
        when(enfoque.espirituDeHoy(APRENDIZ)).thenReturn(espiritu(EstadoEspiritu.ENTREGADO_A_TIEMPO,
                MANANA_EN_LIMA, null));

        assertThat(fallo(proponerResumen(NOCHE_EN_LIMA, "Algo", "Algo mas"))).contains("ya se envio");
        verify(proponerAccion, never()).proponer(any(), any(), any());
    }

    @Test
    @DisplayName("resumen confirmado: entrega por el puerto y traduce el rechazo del negocio")
    void confirmarResumen() {
        EntregarResumenEspirituConfirmable confirmable = new EntregarResumenEspirituConfirmable(enfoque);
        InvocacionHerramienta propuesta = new InvocacionHerramienta(PropuestaDeResumenEspiritu.NOMBRE,
                Map.of(PropuestaDeResumenEspiritu.ARGUMENTO_RESUMEN, "Aprendi",
                        PropuestaDeResumenEspiritu.ARGUMENTO_DIA, "5"));
        when(enfoque.entregarResumenEspiritu(APRENDIZ, 5, "Aprendi")).thenReturn(true);

        assertThat(exito(confirmable.aplicar(APRENDIZ, propuesta))).contains("a tiempo");

        when(enfoque.entregarResumenEspiritu(APRENDIZ, 5, "Aprendi"))
                .thenThrow(new IllegalStateException("Ya se registro este dia"));
        assertThat(fallo(confirmable.aplicar(APRENDIZ, propuesta))).contains("no se envio nada");
    }

    // ─── proponer_iniciar_santuario ──────────────────────────────────────────────────────────

    private ResultadoHerramienta proponerSantuario(Instant ahora) {
        return new PropuestaDeIniciarSantuario(enfoque, proponerAccion, FixedClock.at(ahora)).ejecutar(APRENDIZ,
                new InvocacionHerramienta(PropuestaDeIniciarSantuario.NOMBRE, Map.of()));
    }

    @Test
    @DisplayName("santuario: antes de su hora de Lima no propone y dice desde cuando")
    void santuarioTodaviaNo() {
        Instant desde = Instant.parse("2026-09-24T04:00:00Z"); // 23:00 del miercoles en Lima
        when(enfoque.santuariosDeHoy(APRENDIZ)).thenReturn(List.of(
                new SantuarioDeHoy(LIMA, REGISTRO, "Santuario", true, desde)));

        assertThat(fallo(proponerSantuario(NOCHE_EN_LIMA))).contains("desde las 23:00").contains("faltan 1 h 0 min");
        verify(proponerAccion, never()).proponer(any(), any(), any());
    }

    @Test
    @DisplayName("santuario: ya en su hora, propone iniciarlo con el registro de hoy")
    void proponeSantuario() {
        when(enfoque.santuariosDeHoy(APRENDIZ)).thenReturn(List.of(
                new SantuarioDeHoy(LIMA, REGISTRO, "Santuario", true, NOCHE_EN_LIMA.minusSeconds(60))));

        assertThat(exito(proponerSantuario(NOCHE_EN_LIMA))).contains("Iniciar tu Santuario 'Santuario' ahora");
        verify(proponerAccion).proponer(APRENDIZ, new InvocacionHerramienta(PropuestaDeIniciarSantuario.NOMBRE,
                Map.of(PropuestaDeIniciarSantuario.ARGUMENTO_REGISTRO_ID, REGISTRO.toString())),
                "Iniciar tu Santuario 'Santuario' ahora");
        verify(enfoque, never()).iniciarSantuario(any(), any());
    }

    @Test
    @DisplayName("santuario confirmado: inicia por el puerto; si no es la hora, Fallo legible")
    void confirmarSantuario() {
        IniciarSantuarioConfirmable confirmable = new IniciarSantuarioConfirmable(enfoque);
        InvocacionHerramienta propuesta = new InvocacionHerramienta(PropuestaDeIniciarSantuario.NOMBRE,
                Map.of(PropuestaDeIniciarSantuario.ARGUMENTO_REGISTRO_ID, REGISTRO.toString()));

        assertThat(exito(confirmable.aplicar(APRENDIZ, propuesta))).contains("Santuario iniciado");
        verify(enfoque).iniciarSantuario(APRENDIZ, REGISTRO);

        doThrow(new IllegalStateException("Todavia no es la hora de iniciar tu Santuario"))
                .when(enfoque).iniciarSantuario(APRENDIZ, REGISTRO);
        assertThat(fallo(confirmable.aplicar(APRENDIZ, propuesta))).contains("todavia no es su hora");
    }

    // ─── proponer_iniciar_dia_sin_celular ────────────────────────────────────────────────────

    private ResultadoHerramienta proponerSinCelular(String horas) {
        return new PropuestaDeIniciarDiaSinCelular(enfoque, proponerAccion, FixedClock.at(NOCHE_EN_LIMA))
                .ejecutar(APRENDIZ, new InvocacionHerramienta(PropuestaDeIniciarDiaSinCelular.NOMBRE,
                        Map.of(PropuestaDeIniciarDiaSinCelular.ARGUMENTO_HORAS_OBJETIVO, horas)));
    }

    private void conDiaSinCelular(boolean iniciable, Instant rachaDesde, Integer rachaHoras) {
        when(enfoque.diaSinCelularDeHoy(APRENDIZ)).thenReturn(new DiaSinCelularDeHoy(LIMA, REGISTRO,
                "Dia sin celular", iniciable, rachaDesde, rachaHoras, List.of(3, 6, 9, 12, 15, 18, 21, 24)));
    }

    @Test
    @DisplayName("sin celular: propone con la meta y el registro de hoy, sin iniciar")
    void proponeSinCelular() {
        conDiaSinCelular(true, null, null);

        assertThat(exito(proponerSinCelular(" 12 "))).contains("meta de 12 horas");
        verify(proponerAccion).proponer(APRENDIZ, new InvocacionHerramienta(PropuestaDeIniciarDiaSinCelular.NOMBRE,
                Map.of(PropuestaDeIniciarDiaSinCelular.ARGUMENTO_REGISTRO_ID, REGISTRO.toString(),
                        PropuestaDeIniciarDiaSinCelular.ARGUMENTO_HORAS_OBJETIVO, "12")),
                "Empezar ahora tu 'Dia sin celular' con meta de 12 horas");
        verify(enfoque, never()).iniciarDiaSinCelular(any(), any(), anyInt());
    }

    @Test
    @DisplayName("sin celular: una meta fuera de las del dominio no se propone y se listan las validas")
    void metaInvalida() {
        conDiaSinCelular(true, null, null);

        assertThat(fallo(proponerSinCelular("5"))).contains("3, 6, 9, 12, 15, 18, 21, 24");
        verify(proponerAccion, never()).proponer(any(), any(), any());
    }

    @Test
    @DisplayName("sin celular: con una racha en curso no propone otra, y dice la hora de Lima")
    void rachaEnCurso() {
        conDiaSinCelular(false, Instant.parse("2026-09-24T01:00:00Z"), 6);

        assertThat(fallo(proponerSinCelular("6"))).contains("en curso desde las 20:00")
                .contains("solo puede haber una");
        verify(proponerAccion, never()).proponer(any(), any(), any());
    }

    @Test
    @DisplayName("sin celular confirmado: inicia por el puerto y traduce una racha ya en curso")
    void confirmarSinCelular() {
        IniciarDiaSinCelularConfirmable confirmable = new IniciarDiaSinCelularConfirmable(enfoque);
        InvocacionHerramienta propuesta = new InvocacionHerramienta(PropuestaDeIniciarDiaSinCelular.NOMBRE,
                Map.of(PropuestaDeIniciarDiaSinCelular.ARGUMENTO_REGISTRO_ID, REGISTRO.toString(),
                        PropuestaDeIniciarDiaSinCelular.ARGUMENTO_HORAS_OBJETIVO, "12"));

        assertThat(exito(confirmable.aplicar(APRENDIZ, propuesta))).contains("meta de 12 horas");
        verify(enfoque).iniciarDiaSinCelular(APRENDIZ, REGISTRO, 12);

        doThrow(new IllegalStateException("Ya tienes una racha en curso"))
                .when(enfoque).iniciarDiaSinCelular(APRENDIZ, REGISTRO, 12);
        assertThat(fallo(confirmable.aplicar(APRENDIZ, propuesta))).contains("no se inicio nada");
    }
}
