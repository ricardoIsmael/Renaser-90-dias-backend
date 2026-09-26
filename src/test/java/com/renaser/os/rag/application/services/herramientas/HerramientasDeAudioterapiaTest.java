package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.audioterapia.AudioterapiaSemanalPort;
import com.renaser.os.rag.application.ports.out.audioterapia.AudioterapiaSemanalPort.AudioterapiaDeHoy;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las herramientas de la Audioterapia semanal (D-171): la consulta, la propuesta con las dos
 * preguntas de la app, y la confirmable que la entrega como la app (evidencia de TEXTO + completar),
 * sin pasar por la Pastilla Renacer.
 */
class HerramientasDeAudioterapiaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final UUID REGISTRO = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final String TEXTO = "¿Qué sentiste después de escuchar este audio?\nMucha calma\n\n"
            + "¿Qué te llevas de este audio para tu día de hoy?\nRespirar antes de contestar";

    private final AudioterapiaSemanalPort audioterapia = mock(AudioterapiaSemanalPort.class);
    private final ProponerAccionUseCase proponerAccion = mock(ProponerAccionUseCase.class);

    private static AudioterapiaDeHoy deHoy(String estado) {
        return new AudioterapiaDeHoy(3, "Perdonar", 22, estado == null ? null : REGISTRO, estado);
    }

    private static final AudioterapiaDeHoy SIN_AUDIO = new AudioterapiaDeHoy(null, null, null, REGISTRO, "PENDIENTE");

    private static String exito(ResultadoHerramienta resultado) {
        return ((ResultadoHerramienta.Exito) resultado).contenido();
    }

    private static String fallo(ResultadoHerramienta resultado) {
        return ((ResultadoHerramienta.Fallo) resultado).motivo();
    }

    // ─── consultar_audioterapia ─────────────────────────────────────────────────────────────

    private ResultadoHerramienta consultar() {
        return new ConsultarAudioterapiaHerramienta(audioterapia).ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(ConsultarAudioterapiaHerramienta.NOMBRE));
    }

    @Test
    @DisplayName("consultar: dice la semana, el titulo, cuando cambia y si hoy ya la entrego")
    void consultarConAudio() {
        when(audioterapia.deHoyDe(APRENDIZ)).thenReturn(deHoy("PENDIENTE"));
        assertThat(exito(consultar())).contains("semana 3").contains("'Perdonar'").contains("dia 22")
                .contains("todavia no la entrego");

        when(audioterapia.deHoyDe(APRENDIZ)).thenReturn(deHoy("COMPLETADO"));
        assertThat(exito(consultar())).contains("ya la entrego");

        when(audioterapia.deHoyDe(APRENDIZ)).thenReturn(deHoy(null));
        assertThat(exito(consultar())).contains("Hoy no le toca");
    }

    @Test
    @DisplayName("consultar: sin audio esta semana lo dice; con la cuenta suspendida, un Fallo legible")
    void consultarSinAudioOSinAcceso() {
        when(audioterapia.deHoyDe(APRENDIZ)).thenReturn(SIN_AUDIO);
        assertThat(exito(consultar())).contains("Todavia no hay Audioterapia");

        doThrow(new NotAuthorizedException("Cuenta suspendida")).when(audioterapia).deHoyDe(APRENDIZ);
        assertThat(fallo(consultar())).contains("suspendida");
    }

    // ─── proponer_resumen_audioterapia ─────────────────────────────────────────────────────

    private ResultadoHerramienta proponer(String queSentiste, String queTeLlevas) {
        return new PropuestaDeResumenAudioterapia(audioterapia, proponerAccion).ejecutar(APRENDIZ,
                new InvocacionHerramienta(PropuestaDeResumenAudioterapia.NOMBRE,
                        Map.of("que_sentiste", queSentiste, "que_te_llevas", queTeLlevas)));
    }

    @Test
    @DisplayName("proponer: arma el texto de la app y guarda el registro de hoy, sin entregar nada")
    void propone() {
        when(audioterapia.deHoyDe(APRENDIZ)).thenReturn(deHoy("PENDIENTE"));

        ResultadoHerramienta resultado = proponer(" Mucha calma ", "Respirar antes de contestar");

        String resumen = "Entregar tu Audioterapia de la semana 3 'Perdonar' con tus respuestas: \"" + TEXTO + "\"";
        verify(proponerAccion).proponer(APRENDIZ, new InvocacionHerramienta(PropuestaDeResumenAudioterapia.NOMBRE,
                Map.of("texto", TEXTO, "registro_id", REGISTRO.toString())), resumen);
        verify(audioterapia, never()).entregarRespuestas(any(), any(), anyString());
        assertThat(exito(resultado)).contains("TODAVIA NO esta hecho");
    }

    @Test
    @DisplayName("proponer: sin audio esta semana, ya entregada, cerrada o sin registro hoy, no propone")
    void noPropone() {
        when(audioterapia.deHoyDe(APRENDIZ)).thenReturn(SIN_AUDIO);
        assertThat(fallo(proponer("a", "b"))).contains("todavia no hay Audioterapia");

        when(audioterapia.deHoyDe(APRENDIZ)).thenReturn(deHoy("COMPLETADO"));
        assertThat(fallo(proponer("a", "b"))).contains("ya esta entregada");

        when(audioterapia.deHoyDe(APRENDIZ)).thenReturn(deHoy("EXPIRADO"));
        assertThat(fallo(proponer("a", "b"))).contains("ya se cerro");

        when(audioterapia.deHoyDe(APRENDIZ)).thenReturn(deHoy(null));
        assertThat(fallo(proponer("a", "b"))).contains("Hoy no le toca");

        assertThat(fallo(proponer("a", " "))).contains("Falta una de las dos respuestas");
        verify(proponerAccion, never()).proponer(any(), any(), any());
    }

    // ─── la confirmable ────────────────────────────────────────────────────────────────────

    private final EntregarResumenAudioterapiaConfirmable confirmable =
            new EntregarResumenAudioterapiaConfirmable(audioterapia);
    private static final InvocacionHerramienta PROPUESTA = new InvocacionHerramienta(
            PropuestaDeResumenAudioterapia.NOMBRE, Map.of("texto", TEXTO, "registro_id", REGISTRO.toString()));

    @Test
    @DisplayName("confirmar: entrega el texto al registro de la propuesta y dice los puntos")
    void confirmar() {
        when(audioterapia.entregarRespuestas(APRENDIZ, REGISTRO, TEXTO)).thenReturn(10);

        assertThat(exito(confirmable.aplicar(APRENDIZ, PROPUESTA))).contains("entregada").contains("10");
    }

    @Test
    @DisplayName("confirmar: otro dia, ya entregada o cuenta suspendida vuelven como Fallo legible")
    void confirmarRechazado() {
        doThrow(new NoSuchElementException("Ese registro no es la Audioterapia de hoy"))
                .when(audioterapia).entregarRespuestas(APRENDIZ, REGISTRO, TEXTO);
        assertThat(fallo(confirmable.aplicar(APRENDIZ, PROPUESTA))).contains("ya no es la de hoy");

        doThrow(new IllegalStateException("ya esta COMPLETADO"))
                .when(audioterapia).entregarRespuestas(APRENDIZ, REGISTRO, TEXTO);
        assertThat(fallo(confirmable.aplicar(APRENDIZ, PROPUESTA))).contains("ya estaba entregada");

        doThrow(new NotAuthorizedException("Cuenta suspendida"))
                .when(audioterapia).entregarRespuestas(APRENDIZ, REGISTRO, TEXTO);
        assertThat(fallo(confirmable.aplicar(APRENDIZ, PROPUESTA))).contains("suspendida");
    }

    @Test
    @DisplayName("confirmar: una propuesta sin registro valido no toca habits")
    void confirmarSinRegistro() {
        InvocacionHerramienta rota = new InvocacionHerramienta(PropuestaDeResumenAudioterapia.NOMBRE,
                Map.of("texto", TEXTO, "registro_id", "hoy"));

        assertThat(fallo(confirmable.aplicar(APRENDIZ, rota))).contains("no se entrego nada");
        verify(audioterapia, never()).entregarRespuestas(any(), any(), anyString());
    }
}
