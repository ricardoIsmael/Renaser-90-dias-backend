package com.renaser.os.support.application.services;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.TicketsAlMentor.TextosDelTicket;
import com.renaser.os.support.api.TicketsAlMentor.TicketAlMentor;
import com.renaser.os.support.application.ports.in.ticketmentor.AbrirTicketMentorUseCase;
import com.renaser.os.support.application.ports.in.ticketmentor.AbrirTicketMentorUseCase.AbrirTicketMentorCommand;
import com.renaser.os.support.application.ports.in.ticketmentor.ListarTicketsMentorUseCase;
import com.renaser.os.support.application.ports.in.ticketmentor.ListarTicketsMentorUseCase.TicketsMentorPage;
import com.renaser.os.support.domain.model.ticketmentor.EstadoTicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentorId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code support.api.TicketsAlMentor}: delega en los casos de uso y nunca devuelve tickets ajenos. */
class TicketsAlMentorServiceTest {

    private static final UserId PERSONA = UserId.of(UUID.randomUUID());
    private static final UserId OTRO_APRENDIZ = UserId.of(UUID.randomUUID());
    private static final Instant CREADO = Instant.parse("2026-09-20T12:00:00Z");

    private final ListarTicketsMentorUseCase listar = mock(ListarTicketsMentorUseCase.class);
    private final AbrirTicketMentorUseCase abrir = mock(AbrirTicketMentorUseCase.class);
    private final TicketsAlMentorService service = new TicketsAlMentorService(listar, abrir);

    private static TicketMentor ticketDe(UserId participante, String respuesta) {
        return TicketMentor.rehydrate(TicketMentorId.of(UUID.randomUUID()), participante, "bloqueo", "intentos",
                "impacto", respuesta == null ? EstadoTicketMentor.ABIERTO : EstadoTicketMentor.RESPONDIDO, respuesta,
                respuesta == null ? null : CREADO.plusSeconds(3600), false, CREADO);
    }

    @Test
    @DisplayName("solo devuelve los tickets de quien pregunta, aunque el caso de uso traiga ajenos (cuenta de mentor)")
    void soloPropios() {
        when(listar.propios(PERSONA, null)).thenReturn(new TicketsMentorPage(
                List.of(ticketDe(PERSONA, "Probemos otra hora"), ticketDe(OTRO_APRENDIZ, null)), null));

        List<TicketAlMentor> propios = service.propios(PERSONA);

        assertThat(propios).hasSize(1);
        assertThat(propios.getFirst().respondido()).isTrue();
        assertThat(propios.getFirst().respuestaMentor()).isEqualTo("Probemos otra hora");
        assertThat(propios.getFirst().textos()).isEqualTo(new TextosDelTicket("bloqueo", "intentos", "impacto"));
    }

    @Test
    @DisplayName("abrir corre AbrirTicketMentorUseCase con los tres textos tal cual")
    void abre() {
        AbrirTicketMentorCommand comando = new AbrirTicketMentorCommand(PERSONA, "a", "b", "c");
        TicketMentor abierto = ticketDe(PERSONA, null);
        when(abrir.abrir(comando)).thenReturn(abierto);

        assertThat(service.abrir(PERSONA, new TextosDelTicket("a", "b", "c")).creadoEn()).isEqualTo(CREADO);
        verify(abrir).abrir(comando);
    }
}
