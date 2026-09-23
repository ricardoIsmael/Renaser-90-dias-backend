package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.soporte.GestionarTicketsAlMentorPort;
import com.renaser.os.rag.application.ports.out.soporte.GestionarTicketsAlMentorPort.TextosDelTicket;
import com.renaser.os.rag.application.ports.out.soporte.GestionarTicketsAlMentorPort.TicketAlMentor;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code consultar_mis_tickets_al_mentor}, {@code proponer_ticket_al_mentor} y su escritura: el
 * resumen muestra los tres textos exactos y a quien van, al proponer no se envia nada, y al
 * confirmar se envia exactamente lo guardado.
 */
class TicketAlMentorHerramientasTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final Instant AHORA = Instant.parse("2026-09-24T01:00:00Z");
    private static final TextosDelTicket TEXTOS = new TextosDelTicket("No logro levantarme a las 5",
            "Probe poner la alarma lejos", "Pierdo la roca de CUERPO de la manana");

    private final GestionarTicketsAlMentorPort ticketsPort = mock(GestionarTicketsAlMentorPort.class);
    private final ProponerAccionUseCase proponerAccion = mock(ProponerAccionUseCase.class);
    private final ConsultarMisTicketsAlMentorHerramienta consultar =
            new ConsultarMisTicketsAlMentorHerramienta(ticketsPort, FixedClock.at(AHORA));
    private final PropuestaDeTicketAlMentor proponer = new PropuestaDeTicketAlMentor(ticketsPort, proponerAccion);
    private final AbrirTicketAlMentorConfirmable confirmable = new AbrirTicketAlMentorConfirmable(ticketsPort);

    private static InvocacionHerramienta invocacion(String descripcion, String soluciones, String impacto) {
        return new InvocacionHerramienta(PropuestaDeTicketAlMentor.NOMBRE, Map.of(
                PropuestaDeTicketAlMentor.ARGUMENTO_DESCRIPCION, descripcion,
                PropuestaDeTicketAlMentor.ARGUMENTO_SOLUCIONES, soluciones,
                PropuestaDeTicketAlMentor.ARGUMENTO_IMPACTO, impacto));
    }

    @Test
    @DisplayName("consultar muestra el bloqueo, el estado y la respuesta del mentor")
    void consulta() {
        when(ticketsPort.propios(APRENDIZ)).thenReturn(List.of(
                new TicketAlMentor(TEXTOS, false, null, Instant.parse("2026-09-23T23:00:00Z"), null),
                new TicketAlMentor(TEXTOS, true, "Acuesta a las 21", Instant.parse("2026-09-21T01:00:00Z"),
                        Instant.parse("2026-09-22T01:00:00Z"))));

        String texto = ((ResultadoHerramienta.Exito) consultar.ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(ConsultarMisTicketsAlMentorHerramienta.NOMBRE))).contenido();

        assertThat(texto).contains("- Abierto hace 2 h. Bloqueo: No logro levantarme a las 5")
                .contains("esperando la respuesta del mentor")
                .contains("Estado: respondido hace 2 dias. Respuesta del mentor: Acuesta a las 21");
    }

    @Test
    @DisplayName("sin tickets lo dice; sin permiso, Fallo legible")
    void vacioYSinPermiso() {
        when(ticketsPort.propios(APRENDIZ)).thenReturn(List.of());
        assertThat(consultar.ejecutar(APRENDIZ, InvocacionHerramienta.sinArgumentos("x")))
                .isEqualTo(ResultadoHerramienta.exito("No le abrio ningun ticket a su mentor."));

        when(ticketsPort.propios(APRENDIZ)).thenThrow(new NotAuthorizedException("no"));
        assertThat(consultar.ejecutar(APRENDIZ, InvocacionHerramienta.sinArgumentos("x")))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);
    }

    @Test
    @DisplayName("propone con los tres textos exactos en el resumen, dice que van al mentor, y NO envia")
    void propone() {
        when(ticketsPort.largoMaximoDeCadaTexto()).thenReturn(2000);

        ResultadoHerramienta resultado = proponer.ejecutar(APRENDIZ, invocacion("  No logro levantarme a las 5 ",
                "Probe poner la alarma lejos", "Pierdo la roca de CUERPO de la manana\n"));

        String resumen = "Enviar este ticket a tu mentor:\n"
                + "Que te bloquea: No logro levantarme a las 5\n"
                + "Que ya intentaste: Probe poner la alarma lejos\n"
                + "Como afecta a tu meta SMART: Pierdo la roca de CUERPO de la manana";
        verify(proponerAccion).proponer(APRENDIZ, invocacion(TEXTOS.descripcionBloqueo(),
                TEXTOS.solucionesIntentadas(), TEXTOS.impactoMetaSmart()), resumen);
        verify(ticketsPort, never()).abrir(any(), any());
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido()).contains("TODAVIA NO esta hecho");
    }

    @Test
    @DisplayName("no propone con un texto vacio ni con uno mas largo que lo que acepta support")
    void noProponeInvalido() {
        when(ticketsPort.largoMaximoDeCadaTexto()).thenReturn(10);

        assertThat(proponer.ejecutar(APRENDIZ, invocacion(" ", "b", "c"))).isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(((ResultadoHerramienta.Fallo) proponer.ejecutar(APRENDIZ, invocacion("a", "b", "12345678901")))
                .motivo()).contains("10 caracteres");
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("al confirmar envia exactamente los textos guardados")
    void confirma() {
        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, invocacion(TEXTOS.descripcionBloqueo(),
                TEXTOS.solucionesIntentadas(), TEXTOS.impactoMetaSmart()));

        verify(ticketsPort).abrir(APRENDIZ, TEXTOS);
        assertThat(confirmable.herramienta()).isEqualTo(PropuestaDeTicketAlMentor.NOMBRE);
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido()).startsWith("Ticket enviado a tu mentor.");
    }

    @Test
    @DisplayName("si support lo rechaza al confirmar (no es aprendiz), Fallo legible sin el mensaje crudo")
    void rechazoAlConfirmar() {
        when(ticketsPort.abrir(APRENDIZ, TEXTOS)).thenThrow(new NotAuthorizedException("Solo un aprendiz puede abrir"));

        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, invocacion(TEXTOS.descripcionBloqueo(),
                TEXTOS.solucionesIntentadas(), TEXTOS.impactoMetaSmart()));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo())
                .isEqualTo("Solo una cuenta de aprendiz activa puede abrirle un ticket a su mentor: no se envio nada.");
    }
}
