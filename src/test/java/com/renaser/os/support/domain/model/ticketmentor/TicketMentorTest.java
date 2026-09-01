package com.renaser.os.support.domain.model.ticketmentor;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketMentorTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    private static final TicketMentorId ID = TicketMentorId.of(
            UUID.fromString("11111111-1111-1111-1111-111111111111"));

    private static UserId nuevoParticipante() {
        return UserId.of(UUID.randomUUID());
    }

    private static TicketMentor ticketAbierto() {
        return TicketMentor.abrir(ID, nuevoParticipante(), "No puedo mantener la racha de Santuario",
                "Probe apagar notificaciones", "Atrasa mi meta de 90 dias sin celular", CLOCK);
    }

    @Test
    @DisplayName("abrir() nace ABIERTO, sin respuesta y sin guardar en biblioteca")
    void abrirNaceAbierto() {
        TicketMentor ticket = ticketAbierto();

        assertThat(ticket.id()).isEqualTo(ID);
        assertThat(ticket.estado()).isEqualTo(EstadoTicketMentor.ABIERTO);
        assertThat(ticket.respuestaMentor()).isNull();
        assertThat(ticket.respondidoEn()).isNull();
        assertThat(ticket.guardadoEnBiblioteca()).isFalse();
        assertThat(ticket.creadoEn()).isEqualTo(CLOCK.now());
    }

    @Test
    @DisplayName("los tres campos son obligatorios (docs/FEATURE_TICKETS.md: 'A ticket cannot be created without all three fields')")
    void losTresCamposSonObligatorios() {
        UserId participante = nuevoParticipante();

        assertThatThrownBy(() -> TicketMentor.abrir(ID, participante, "  ", "algo", "algo", CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TicketMentor.abrir(ID, participante, "algo", null, "algo", CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TicketMentor.abrir(ID, participante, "algo", "algo", "", CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("responder() transiciona a RESPONDIDO y registra la respuesta")
    void responderTransicionaARespondido() {
        TicketMentor ticket = ticketAbierto();

        ticket.responder("Reduci las notificaciones a solo llamadas", CLOCK);

        assertThat(ticket.estado()).isEqualTo(EstadoTicketMentor.RESPONDIDO);
        assertThat(ticket.respuestaMentor()).isEqualTo("Reduci las notificaciones a solo llamadas");
        assertThat(ticket.respondidoEn()).isEqualTo(CLOCK.now());
    }

    @Test
    @DisplayName("responder() con respuesta vacia falla")
    void responderConRespuestaVaciaFalla() {
        TicketMentor ticket = ticketAbierto();

        assertThatThrownBy(() -> ticket.responder("   ", CLOCK)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un ticket ya respondido no se puede volver a responder (nunca se reabre)")
    void noSePuedeResponderDosVeces() {
        TicketMentor ticket = ticketAbierto();
        ticket.responder("Primera respuesta", CLOCK);

        assertThatThrownBy(() -> ticket.responder("Segunda respuesta", CLOCK))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ticket.respuestaMentor()).isEqualTo("Primera respuesta");
    }

    @Test
    @DisplayName("guardarEnBiblioteca() exige RESPONDIDO (CHECK respondido_coherente del SQL)")
    void guardarEnBibliotecaExigeRespondido() {
        TicketMentor ticket = ticketAbierto();

        assertThatThrownBy(ticket::guardarEnBiblioteca).isInstanceOf(IllegalStateException.class);
        assertThat(ticket.guardadoEnBiblioteca()).isFalse();
    }

    @Test
    @DisplayName("guardarEnBiblioteca() sobre un ticket respondido marca el flag")
    void guardarEnBibliotecaSobreRespondidoFunciona() {
        TicketMentor ticket = ticketAbierto();
        ticket.responder("Una buena respuesta", CLOCK);

        ticket.guardarEnBiblioteca();

        assertThat(ticket.guardadoEnBiblioteca()).isTrue();
    }

    @Test
    @DisplayName("rehydrate() reconstruye un ticket ya existente tal cual (para el adaptador de persistencia)")
    void rehydrateReconstruyeSinValidar() {
        TicketMentorId id = TicketMentorId.of(UUID.randomUUID());
        UserId participante = nuevoParticipante();

        TicketMentor ticket = TicketMentor.rehydrate(id, participante, "bloqueo", "soluciones", "impacto",
                EstadoTicketMentor.RESPONDIDO, "respuesta", CLOCK.now(), true, CLOCK.now());

        assertThat(ticket.id()).isEqualTo(id);
        assertThat(ticket.estado()).isEqualTo(EstadoTicketMentor.RESPONDIDO);
        assertThat(ticket.guardadoEnBiblioteca()).isTrue();
    }
}
