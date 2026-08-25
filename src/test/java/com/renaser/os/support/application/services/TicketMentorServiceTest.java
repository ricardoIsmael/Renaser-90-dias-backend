package com.renaser.os.support.application.services;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.TicketMentorAbiertoEvent;
import com.renaser.os.support.api.TicketMentorRespondidoEvent;
import com.renaser.os.support.application.ports.in.ticketmentor.AbrirTicketMentorUseCase.AbrirTicketMentorCommand;
import com.renaser.os.support.application.ports.in.ticketmentor.BuscarBibliotecaUseCase.BuscarBibliotecaCommand;
import com.renaser.os.support.application.ports.in.ticketmentor.GuardarEnBibliotecaUseCase.GuardarEnBibliotecaCommand;
import com.renaser.os.support.application.ports.in.ticketmentor.ResponderTicketMentorUseCase.ResponderTicketMentorCommand;
import com.renaser.os.support.domain.model.ticketmentor.EstadoTicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentorId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketMentorServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private InMemoryTicketMentorPort port;
    private FakeUserSummaryFinder actores;
    private RecordingEventPublisher events;
    private TicketMentorService service;

    private UserId trainee;
    private UserId mentor;
    private UserId admin;

    @BeforeEach
    void setUp() {
        port = new InMemoryTicketMentorPort();
        actores = new FakeUserSummaryFinder();
        events = new RecordingEventPublisher();
        service = new TicketMentorService(port, port, port, actores, events, CLOCK);

        trainee = UserId.of(UUID.randomUUID());
        mentor = UserId.of(UUID.randomUUID());
        admin = UserId.of(UUID.randomUUID());
        actores.conActor(trainee, UserRole.TRAINEE).conActor(mentor, UserRole.MENTOR)
                .conActor(admin, UserRole.ADMIN);
    }

    private TicketMentor abrirTicketDeTrainee() {
        return service.abrir(new AbrirTicketMentorCommand(trainee, "Bloqueo", "Solucion intentada", "Impacto SMART"));
    }

    @Test
    @DisplayName("un TRAINEE puede abrir un ticket y queda ABIERTO")
    void traineePuedeAbrirTicket() {
        TicketMentor ticket = abrirTicketDeTrainee();

        assertThat(ticket.estado()).isEqualTo(EstadoTicketMentor.ABIERTO);
        assertThat(ticket.participanteId()).isEqualTo(trainee);
        assertThat(events.eventosPublicados()).hasSize(1).first().isInstanceOf(TicketMentorAbiertoEvent.class);
    }

    @Test
    @DisplayName("seguridad: un MENTOR no puede abrir un ticket (solo el aprendiz)")
    void mentorNoPuedeAbrirTicket() {
        assertThatThrownBy(() -> service.abrir(new AbrirTicketMentorCommand(mentor, "Bloqueo", "Solucion", "Impacto")))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("seguridad: un actor SUSPENDED no puede abrir un ticket aunque su rol sea TRAINEE")
    void suspendidoNoPuedeAbrirTicket() {
        UserId suspendido = UserId.of(UUID.randomUUID());
        actores.conActor(suspendido, UserRole.TRAINEE, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> service.abrir(new AbrirTicketMentorCommand(suspendido, "Bloqueo", "Solucion",
                "Impacto"))).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("un MENTOR puede responder un ticket ABIERTO")
    void mentorPuedeResponder() {
        TicketMentor ticket = abrirTicketDeTrainee();

        TicketMentor respondido = service.responder(new ResponderTicketMentorCommand(ticket.id(), mentor,
                "Reduci las notificaciones"));

        assertThat(respondido.estado()).isEqualTo(EstadoTicketMentor.RESPONDIDO);
        assertThat(respondido.respuestaMentor()).isEqualTo("Reduci las notificaciones");
        assertThat(events.eventosPublicados()).hasSize(2).last().isInstanceOf(TicketMentorRespondidoEvent.class);
    }

    @Test
    @DisplayName("seguridad: un TRAINEE no puede responder su propio ticket")
    void traineeNoPuedeResponder() {
        TicketMentor ticket = abrirTicketDeTrainee();

        assertThatThrownBy(() -> service.responder(new ResponderTicketMentorCommand(ticket.id(), trainee,
                "Me respondo yo mismo"))).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("responder() sobre un ticket que no existe lanza NoSuchElementException")
    void responderTicketInexistente() {
        assertThatThrownBy(() -> service.responder(new ResponderTicketMentorCommand(
                TicketMentorId.newId(), mentor, "respuesta"))).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("un MENTOR puede guardar en biblioteca un ticket ya respondido")
    void mentorPuedeGuardarEnBiblioteca() {
        TicketMentor ticket = abrirTicketDeTrainee();
        service.responder(new ResponderTicketMentorCommand(ticket.id(), mentor, "Respuesta"));

        TicketMentor guardado = service.guardar(new GuardarEnBibliotecaCommand(ticket.id(), mentor));

        assertThat(guardado.guardadoEnBiblioteca()).isTrue();
    }

    @Test
    @DisplayName("seguridad: un ADMIN no puede guardar en biblioteca (no es el mentor asignado)")
    void adminNoPuedeGuardarEnBiblioteca() {
        TicketMentor ticket = abrirTicketDeTrainee();
        service.responder(new ResponderTicketMentorCommand(ticket.id(), mentor, "Respuesta"));

        assertThatThrownBy(() -> service.guardar(new GuardarEnBibliotecaCommand(ticket.id(), admin)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("buscar(): TRAINEE y MENTOR pueden buscar en la biblioteca")
    void traineeYMentorPuedenBuscar() {
        TicketMentor ticket = abrirTicketDeTrainee();
        service.responder(new ResponderTicketMentorCommand(ticket.id(), mentor, "Respuesta util"));
        service.guardar(new GuardarEnBibliotecaCommand(ticket.id(), mentor));

        var resultados = service.buscar(new BuscarBibliotecaCommand(trainee, "bloqueo"));

        assertThat(resultados).hasSize(1);
        assertThat(resultados.getFirst()).contains("Pregunta:").contains("Respuesta:");
    }

    @Test
    @DisplayName("seguridad: un ADMIN no puede buscar en la biblioteca (TICKETS-05 es solo TRAINEE/MENTOR)")
    void adminNoPuedeBuscarEnBiblioteca() {
        assertThatThrownBy(() -> service.buscar(new BuscarBibliotecaCommand(admin, "algo")))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("propios(): un TRAINEE ve solo sus propios tickets")
    void traineeVeSoloLosSuyos() {
        UserId otroTrainee = UserId.of(UUID.randomUUID());
        actores.conActor(otroTrainee, UserRole.TRAINEE);
        abrirTicketDeTrainee();
        service.abrir(new AbrirTicketMentorCommand(otroTrainee, "Otro bloqueo", "Otra solucion", "Otro impacto"));

        var pagina = service.propios(trainee, null);

        assertThat(pagina.tickets()).hasSize(1);
        assertThat(pagina.tickets().getFirst().participanteId()).isEqualTo(trainee);
    }

    @Test
    @DisplayName("seguridad: un ADMIN no puede usar la vista propios() (debe usar todos())")
    void adminNoPuedeUsarPropios() {
        assertThatThrownBy(() -> service.propios(admin, null)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("todos(): un ADMIN ve todos los tickets de la plataforma")
    void adminVeTodos() {
        abrirTicketDeTrainee();

        var pagina = service.todos(admin, null);

        assertThat(pagina.tickets()).hasSize(1);
    }

    @Test
    @DisplayName("seguridad: un TRAINEE no puede usar la vista todos() (es de plataforma, solo MENTOR_LEAD/ADMIN/ALCHEMIST)")
    void traineeNoPuedeUsarTodos() {
        assertThatThrownBy(() -> service.todos(trainee, null)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("el rol no se puede inyectar: el comando de apertura no tiene campo de rol/estado")
    void elComandoDeAperturaNoExponeRolNiEstado() {
        // Verificacion estructural: AbrirTicketMentorCommand solo tiene 4 componentes
        // (participanteId + los 3 campos de negocio) — no hay forma de que un cliente
        // mande "estado":"RESPONDIDO" o "guardadoEnBiblioteca":true en el alta.
        var command = new AbrirTicketMentorCommand(trainee, "a", "b", "c");
        assertThat(command.getClass().getRecordComponents()).hasSize(4);

        TicketMentor ticket = service.abrir(command);
        assertThat(ticket.estado()).isEqualTo(EstadoTicketMentor.ABIERTO);
        assertThat(ticket.guardadoEnBiblioteca()).isFalse();
    }
}
