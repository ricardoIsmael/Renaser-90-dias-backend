package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.TicketMentorAbiertoEvent;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E-217: el aprendiz abria un ticket y el mentor no se enteraba. Contra el codigo viejo estos
 * tests no compilan siquiera: no existia ningun listener de {@link TicketMentorAbiertoEvent}.
 */
@ExtendWith(MockitoExtension.class)
class TicketMentorAbiertoNotificationListenerTest {

    @Mock
    private EmitirNotificacionUseCase emitirNotificacionUseCase;
    @Mock
    private ParticipacionProgramaFinder participacionFinder;
    @Mock
    private UserSummaryFinder userSummaryFinder;

    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private final UserId mentor = UserId.of(UUID.randomUUID());
    private final UUID ticketId = UUID.randomUUID();

    private TicketMentorAbiertoNotificationListener listener() {
        return new TicketMentorAbiertoNotificationListener(emitirNotificacionUseCase, participacionFinder,
                userSummaryFinder);
    }

    private TicketMentorAbiertoEvent evento() {
        return new TicketMentorAbiertoEvent(ticketId, aprendiz, Instant.parse("2026-09-23T15:00:00Z"));
    }

    private ParticipacionPrograma participacionCon(UserId mentorAsignado) {
        return new ParticipacionPrograma(aprendiz, true, 10, LocalDate.of(2026, 9, 14),
                ZoneId.of("America/Lima"), null, UUID.randomUUID(), mentorAsignado, UserRole.TRAINEE, false, true);
    }

    private void aprendizLlamado(String nombre) {
        when(userSummaryFinder.findById(aprendiz)).thenReturn(Optional.of(
                new UserSummary(aprendiz, nombre, null, UserRole.TRAINEE, UserStatus.ACTIVE)));
    }

    private EmitirNotificacionCommand comandoEmitido() {
        ArgumentCaptor<EmitirNotificacionCommand> captor = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitirNotificacionUseCase).emitir(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("con mentor asignado: le llega UNA notificacion TICKET_ABIERTO, con el ticket como clave")
    void avisaAlMentorAsignado() {
        when(participacionFinder.deParticipante(aprendiz)).thenReturn(Optional.of(participacionCon(mentor)));
        aprendizLlamado("Ana Quispe");

        listener().on(evento());

        EmitirNotificacionCommand comando = comandoEmitido();
        assertThat(comando.usuarioId()).isEqualTo(mentor);
        assertThat(comando.tipo()).isEqualTo(TipoNotificacion.TICKET_ABIERTO);
        // El indice unico (usuario, tipo, origen) descarta la reentrega del outbox (C-7).
        assertThat(comando.origenEventoId()).isEqualTo(ticketId);
        assertThat(comando.cuerpo()).contains("Ana Quispe");
    }

    @Test
    @DisplayName("sin mentor asignado: no se avisa a nadie, ni se inventa otro destinatario")
    void sinMentorNoEmiteNada() {
        when(participacionFinder.deParticipante(aprendiz)).thenReturn(Optional.of(participacionCon(null)));

        listener().on(evento());

        verify(emitirNotificacionUseCase, never()).emitir(any());
    }

    @Test
    @DisplayName("aprendiz inexistente: no explota ni emite")
    void sinParticipacionNoEmiteNada() {
        when(participacionFinder.deParticipante(aprendiz)).thenReturn(Optional.empty());

        listener().on(evento());

        verify(emitirNotificacionUseCase, never()).emitir(any());
    }

    /**
     * El evento no trae el texto del bloqueo, y asi tiene que seguir: el cuerpo sale tambien por
     * push a la pantalla bloqueada del mentor. Se fija el texto exacto para que nadie lo "enriquezca"
     * con el contenido del ticket sin pasar por aca.
     */
    @Test
    @DisplayName("el texto solo dice quien abrio el ticket, nunca su contenido")
    void elTextoNoLlevaElContenidoDelTicket() {
        when(participacionFinder.deParticipante(aprendiz)).thenReturn(Optional.of(participacionCon(mentor)));
        aprendizLlamado("Ana Quispe");

        listener().on(evento());

        EmitirNotificacionCommand comando = comandoEmitido();
        assertThat(comando.titulo()).isEqualTo("Nuevo ticket de un aprendiz");
        assertThat(comando.cuerpo()).isEqualTo("Ana Quispe te abrió un ticket y espera tu respuesta.");
    }

    @Test
    @DisplayName("sin nombre conocido del aprendiz, el aviso sale igual con un texto generico")
    void sinNombreUsaTextoGenerico() {
        when(participacionFinder.deParticipante(aprendiz)).thenReturn(Optional.of(participacionCon(mentor)));
        when(userSummaryFinder.findById(aprendiz)).thenReturn(Optional.empty());

        listener().on(evento());

        assertThat(comandoEmitido().cuerpo()).isEqualTo("Un aprendiz te abrió un ticket y espera tu respuesta.");
    }
}
