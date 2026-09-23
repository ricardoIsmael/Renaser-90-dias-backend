package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.calendario.ConsultarEventosDelAprendizPort;
import com.renaser.os.rag.application.ports.out.calendario.ConsultarEventosDelAprendizPort.AgendaDeEventos;
import com.renaser.os.rag.application.ports.out.calendario.ConsultarEventosDelAprendizPort.EventoDeLaAgenda;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code consultar_eventos}: la herramienta no calcula "hoy" ni decide que eventos se ven — eso es
 * de {@code calendar}, probado en {@code EventosDelParticipanteServiceTest} con un reloj a las 03:00
 * UTC. Aca se prueba el argumento, la traduccion de fallos y el texto.
 */
class ConsultarEventosHerramientaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate HOY = LocalDate.of(2026, 9, 23);

    private final ConsultarEventosDelAprendizPort puerto = mock(ConsultarEventosDelAprendizPort.class);
    private final ConsultarEventosHerramienta herramienta = new ConsultarEventosHerramienta(puerto);

    @Test
    @DisplayName("sin alcance es la semana: hoy y los seis dias siguientes, en hora local y con la asistencia")
    void semanaPorDefecto() {
        when(puerto.proximosDias(APRENDIZ, 7)).thenReturn(new AgendaDeEventos(HOY, HOY.plusDays(6), List.of(
                new EventoDeLaAgenda("Sesion de celula", LocalDateTime.of(2026, 9, 23, 20, 0),
                        LocalDateTime.of(2026, 9, 23, 21, 30), "ASISTE"),
                new EventoDeLaAgenda("Retiro", LocalDateTime.of(2026, 9, 26, 23, 0),
                        LocalDateTime.of(2026, 9, 27, 1, 0), null),
                new EventoDeLaAgenda("Anuncio", LocalDateTime.of(2026, 9, 28, 9, 0), null, "QUIZAS"))));

        var invocacion = InvocacionHerramienta.sinArgumentos(ConsultarEventosHerramienta.NOMBRE);
        String texto = texto(herramienta.ejecutar(APRENDIZ, invocacion));

        assertThat(herramienta.definicion().obligatoriosFaltantesEn(invocacion)).isEmpty();
        assertThat(texto).contains("Eventos del 2026-09-23 al 2026-09-29 (hora local)")
                .contains("- 2026-09-23 20:00 a 21:30 | Sesion de celula | asistencia=confirmo que va")
                .contains("- 2026-09-26 23:00 a 2026-09-27 01:00 | Retiro | asistencia=sin responder")
                .contains("- 2026-09-28 09:00 | Anuncio | asistencia=quizas");
    }

    @Test
    @DisplayName("'hoy' pide un solo dia y, si no hay nada, lo dice")
    void hoySinEventos() {
        when(puerto.proximosDias(APRENDIZ, 1)).thenReturn(new AgendaDeEventos(HOY, HOY, List.of()));

        String texto = texto(herramienta.ejecutar(APRENDIZ, conAlcance(" HOY ")));

        assertThat(texto).isEqualTo("No tiene eventos del calendario del 2026-09-23.");
        verify(puerto).proximosDias(APRENDIZ, 1);
    }

    @Test
    @DisplayName("un alcance que no existe es un fallo legible y no consulta nada")
    void alcanceInvalido() {
        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, conAlcance("mes"));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("hoy o semana");
        verifyNoInteractions(puerto);
    }

    @Test
    @DisplayName("una cuenta suspendida recibe un fallo legible, sin el mensaje de la excepcion")
    void suspendida() {
        when(puerto.proximosDias(eq(APRENDIZ), anyInt())).thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, conAlcance("semana"));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("suspendida");
    }

    private static InvocacionHerramienta conAlcance(String alcance) {
        return new InvocacionHerramienta(ConsultarEventosHerramienta.NOMBRE,
                Map.of(ConsultarEventosHerramienta.ARGUMENTO_ALCANCE, alcance));
    }

    private static String texto(ResultadoHerramienta resultado) {
        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        return ((ResultadoHerramienta.Exito) resultado).contenido();
    }
}
