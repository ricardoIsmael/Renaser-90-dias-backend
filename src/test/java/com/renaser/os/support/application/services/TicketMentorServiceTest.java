package com.renaser.os.support.application.services;

import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.TicketMentorAbiertoEvent;
import com.renaser.os.support.api.TicketMentorRespondidoEvent;
import com.renaser.os.support.application.ports.in.ticketmentor.AbrirTicketMentorUseCase.AbrirTicketMentorCommand;
import com.renaser.os.support.application.ports.in.ticketmentor.BuscarBibliotecaUseCase.BuscarBibliotecaCommand;
import com.renaser.os.support.application.ports.in.ticketmentor.GuardarEnBibliotecaUseCase.GuardarEnBibliotecaCommand;
import com.renaser.os.support.application.ports.in.ticketmentor.ResponderTicketMentorUseCase.ResponderTicketMentorCommand;
import com.renaser.os.support.application.ports.in.ticketmentor.TicketConAutor;
import com.renaser.os.support.domain.model.ticketmentor.EstadoTicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentorId;
import com.renaser.os.users.api.AlcanceDeFormacionPermisivo;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketMentorServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private InMemoryTicketMentorPort port;
    private FakeUserSummaryFinder actores;
    private FakeParticipacionProgramaFinder participaciones;
    private FakeAprendicesDelAcompanante acompanamiento;
    private RecordingEventPublisher events;
    private TicketMentorService service;

    private UserId trainee;
    private UserId mentor;
    private UserId admin;
    private UserId lider;
    private UserId liderSuspendido;
    /** Spec 056: el líder tiene activa la Escuela; el aprendiz de esta prueba es de la Escuela salvo que se diga otra cosa. */
    private Formacion formacionDelAprendiz = Formacion.ESCUELA;
    private final java.util.Map<UserId, Formacion> formacionDeCadaAprendiz = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        port = new InMemoryTicketMentorPort();
        actores = new FakeUserSummaryFinder();
        participaciones = new FakeParticipacionProgramaFinder();
        acompanamiento = new FakeAprendicesDelAcompanante();
        events = new RecordingEventPublisher();
        lider = UserId.of(UUID.randomUUID());
        liderSuspendido = UserId.of(UUID.randomUUID());
        service = new TicketMentorService(port, port, port, actores, participaciones, acompanamiento, alcanceDelLider(), events, CLOCK,
                idsSecuenciales());

        trainee = UserId.of(UUID.randomUUID());
        mentor = UserId.of(UUID.randomUUID());
        admin = UserId.of(UUID.randomUUID());
        actores.conActor(trainee, UserRole.TRAINEE).conActor(mentor, UserRole.MENTOR)
                .conActor(admin, UserRole.ADMIN).conActor(lider, UserRole.MENTOR_LEAD)
                .conActor(liderSuspendido, UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);
        participaciones.conMentorAsignado(trainee, mentor);
        acompanamiento.acompana(mentor, trainee);
    }

    /** Spec 056: el líder ve solo la Escuela; Administración y los demás, todas; cada aprendiz es de la formación que diga la prueba. */
    private AlcanceDeFormacionPermisivo alcanceDelLider() {
        return new AlcanceDeFormacionPermisivo() {
            @Override
            public AlcanceDeFormacion de(UserId actor) {
                return lider.equals(actor) || liderSuspendido.equals(actor) ? AlcanceDeFormacion.de(Formacion.ESCUELA) : AlcanceDeFormacion.todas();
            }

            @Override
            public Set<Formacion> formacionesDe(UserId persona) {
                return Set.of(formacionDeCadaAprendiz.getOrDefault(persona, formacionDelAprendiz));
            }

            @Override
            public Set<UserId> dentroDelAlcance(java.util.Collection<UserId> personas, AlcanceDeFormacion alcance) {
                return personas.stream().filter(p -> formacionesDe(p).stream().anyMatch(alcance::incluye))
                        .collect(java.util.stream.Collectors.toSet());
            }
        };
    }

    /**
     * Identidad determinista: con el id entrando por el puerto {@code IdGenerator}, la factoria
     * del agregado ya no lo sortea. Es <b>secuencial</b>, no fijo, porque hay tests que abren mas
     * de un ticket y el fake en memoria los indexa por id — un id fijo los colapsaria en uno solo.
     */
    private static IdGenerator idsSecuenciales() {
        AtomicInteger contador = new AtomicInteger();
        return () -> UUID.fromString("00000000-0000-4000-8000-%012d".formatted(contador.incrementAndGet()));
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
        // E-217: notifications usa el id del ticket como clave de deduplicacion y el aprendiz para
        // encontrar a su mentor; los dos tienen que viajar en el evento.
        TicketMentorAbiertoEvent evento = (TicketMentorAbiertoEvent) events.eventosPublicados().getFirst();
        assertThat(evento.ticketId()).isEqualTo(ticket.id().value());
        assertThat(evento.participanteId()).isEqualTo(trainee);
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
                "Reduci las notificaciones")).ticket();

        assertThat(respondido.estado()).isEqualTo(EstadoTicketMentor.RESPONDIDO);
        assertThat(respondido.respuestaMentor()).isEqualTo("Reduci las notificaciones");
        // D-241: queda QUIEN respondio, para que un cambio de grupo posterior no le reatribuya el trabajo.
        assertThat(respondido.respondidoPor()).isEqualTo(mentor);
        assertThat(events.eventosPublicados()).hasSize(2).last().isInstanceOf(TicketMentorRespondidoEvent.class);
    }

    @Test
    @DisplayName("D-265: el guía de aprendiz asignado responde, archiva y busca como un mentor")
    void guiaDeAprendizHaceLoMismoQueElMentor() {
        UserId guia = UserId.of(UUID.randomUUID());
        UserId aprendizDelGuia = UserId.of(UUID.randomUUID());
        actores.conActor(guia, UserRole.TRAINEE_GUIDE).conActor(aprendizDelGuia, UserRole.TRAINEE);
        participaciones.conMentorAsignado(aprendizDelGuia, guia);
        acompanamiento.acompana(guia, aprendizDelGuia);
        TicketMentor ticket = service.abrir(new AbrirTicketMentorCommand(aprendizDelGuia, "Bloqueo", "Intento",
                "Impacto"));

        TicketMentor respondido = service.responder(new ResponderTicketMentorCommand(ticket.id(), guia, "Respuesta")).ticket();
        service.guardar(new GuardarEnBibliotecaCommand(ticket.id(), guia));

        assertThat(respondido.respondidoPor()).isEqualTo(guia);
        assertThat(service.buscar(new BuscarBibliotecaCommand(guia, "bloqueo"))).hasSize(1);
        assertThat(service.propios(guia, null).tickets()).isNotEmpty();
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
                TicketMentorId.of(UUID.randomUUID()), mentor, "respuesta")))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("un MENTOR puede guardar en biblioteca un ticket ya respondido")
    void mentorPuedeGuardarEnBiblioteca() {
        TicketMentor ticket = abrirTicketDeTrainee();
        service.responder(new ResponderTicketMentorCommand(ticket.id(), mentor, "Respuesta"));

        TicketMentor guardado = service.guardar(new GuardarEnBibliotecaCommand(ticket.id(), mentor)).ticket();

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

    /**
     * E-38: el rol MENTOR no alcanza — antes cualquier mentor podia responder o archivar el
     * ticket de un aprendiz ajeno, aunque el mensaje de error ya decia "solo el mentor
     * asignado". Encontrado probando endpoints contra la app real.
     */
    @Test
    @DisplayName("E-38: un MENTOR que NO es el asignado al aprendiz no puede responder su ticket")
    void mentorNoAsignadoNoPuedeResponder() {
        TicketMentor ticket = abrirTicketDeTrainee();
        UserId otroMentor = UserId.of(UUID.randomUUID());
        actores.conActor(otroMentor, UserRole.MENTOR);

        assertThatThrownBy(() -> service.responder(
                new ResponderTicketMentorCommand(ticket.id(), otroMentor, "respuesta de un ajeno")))
                .isInstanceOf(NotAuthorizedException.class);
        assertThat(port.byId(ticket.id()).orElseThrow().estado()).isEqualTo(EstadoTicketMentor.ABIERTO);
    }

    @Test
    @DisplayName("E-38: un MENTOR que NO es el asignado tampoco puede archivar el ticket en la biblioteca")
    void mentorNoAsignadoNoPuedeGuardarEnBiblioteca() {
        TicketMentor ticket = abrirTicketDeTrainee();
        service.responder(new ResponderTicketMentorCommand(ticket.id(), mentor, "respuesta legitima"));
        UserId otroMentor = UserId.of(UUID.randomUUID());
        actores.conActor(otroMentor, UserRole.MENTOR);

        assertThatThrownBy(() -> service.guardar(new GuardarEnBibliotecaCommand(ticket.id(), otroMentor)))
                .isInstanceOf(NotAuthorizedException.class);
        assertThat(port.byId(ticket.id()).orElseThrow().guardadoEnBiblioteca()).isFalse();
    }

    // ── D19: un mentor o una guía ve solo los tickets de los aprendices que acompaña ────────────────────────────────

    private UserId aprendizAjeno() {
        UserId ajeno = UserId.of(UUID.randomUUID());
        actores.conActor(ajeno, UserRole.TRAINEE);
        return ajeno;
    }

    @Test
    @DisplayName("propios(): un MENTOR ve solo los tickets de los aprendices que acompaña, no los de los demás")
    void mentorVeSoloLosTicketsDeSusAprendices() {
        TicketMentor delMio = abrirTicketDeTrainee();
        UserId ajeno = aprendizAjeno();
        service.abrir(new AbrirTicketMentorCommand(ajeno, "Bloqueo ajeno", "Solucion ajena", "Impacto ajeno"));

        var pagina = service.propios(mentor, null);

        assertThat(pagina.tickets()).extracting(TicketMentor::id).containsExactly(delMio.id());
    }

    @Test
    @DisplayName("propios(): una guía de aprendiz (TRAINEE_GUIDE) ve solo los tickets de los aprendices que acompaña")
    void guiaVeSoloLosTicketsDeSusAprendices() {
        UserId guia = UserId.of(UUID.randomUUID());
        actores.conActor(guia, UserRole.TRAINEE_GUIDE);
        UserId aprendizDeLaGuia = aprendizAjeno();
        acompanamiento.acompana(guia, aprendizDeLaGuia);
        abrirTicketDeTrainee();
        TicketMentor delaGuia = service.abrir(new AbrirTicketMentorCommand(aprendizDeLaGuia, "Bloqueo", "Solucion", "Impacto"));

        assertThat(service.propios(guia, null).tickets()).extracting(TicketMentor::id).containsExactly(delaGuia.id());
        // y el mentor no ve los de la guía
        assertThat(service.propios(mentor, null).tickets()).extracting(TicketMentor::participanteId).containsExactly(trainee);
    }

    @Test
    @DisplayName("propios(): quien no acompaña a nadie recibe una página vacía, nunca todos los tickets")
    void mentorSinAprendicesVeUnaPaginaVacia() {
        abrirTicketDeTrainee();
        UserId sinGrupo = UserId.of(UUID.randomUUID());
        actores.conActor(sinGrupo, UserRole.MENTOR);

        var pagina = service.propios(sinGrupo, null);

        assertThat(pagina.tickets()).isEmpty();
        assertThat(pagina.nextCursor()).isNull();
    }

    @Test
    @DisplayName("propios(): un mentor que dejó de acompañar al aprendiz ya no ve sus tickets")
    void mentorQueDejoDeAcompanarYaNoLosVe() {
        abrirTicketDeTrainee();
        assertThat(service.propios(mentor, null).tickets()).hasSize(1);

        acompanamiento.dejaDeAcompanar(mentor, trainee);

        assertThat(service.propios(mentor, null).tickets()).isEmpty();
    }

    @Test
    @DisplayName("seguridad: una cuenta suspendida de mentor recibe 403 en propios() aunque acompañe a alguien")
    void mentorSuspendidoNoListaTickets() {
        UserId suspendido = UserId.of(UUID.randomUUID());
        actores.conActor(suspendido, UserRole.MENTOR, UserStatus.SUSPENDED);
        acompanamiento.acompana(suspendido, trainee);
        abrirTicketDeTrainee();

        assertThatThrownBy(() -> service.propios(suspendido, null)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("todos(): ADMIN, ALCHEMIST y MENTOR_LEAD ven todos los tickets; un MENTOR o una guía reciben 403")
    void todosEsDeLaPlataformaYNoDelMentor() {
        abrirTicketDeTrainee();
        service.abrir(new AbrirTicketMentorCommand(aprendizAjeno(), "Bloqueo ajeno", "Solucion ajena", "Impacto ajeno"));
        UserId alquimista = UserId.of(UUID.randomUUID());
        UserId lider = UserId.of(UUID.randomUUID());
        UserId guia = UserId.of(UUID.randomUUID());
        actores.conActor(alquimista, UserRole.ALCHEMIST).conActor(lider, UserRole.MENTOR_LEAD).conActor(guia, UserRole.TRAINEE_GUIDE);

        assertThat(service.todos(admin, null).tickets()).hasSize(2);
        assertThat(service.todos(alquimista, null).tickets()).hasSize(2);
        assertThat(service.todos(lider, null).tickets()).hasSize(2);
        assertThatThrownBy(() -> service.todos(mentor, null)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.todos(guia, null)).isInstanceOf(NotAuthorizedException.class);
        // ADMIN y ALCHEMIST siguen sin usar la vista de «los míos»: su puerta es todos()
        assertThatThrownBy(() -> service.propios(alquimista, null)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("E-38 y D19: una guía que no es la asignada al aprendiz no puede responder ni archivar su ticket, aunque acompañe a otros")
    void guiaNoAsignadaNoPuedeResponderNiArchivar() {
        TicketMentor ticket = abrirTicketDeTrainee();
        service.responder(new ResponderTicketMentorCommand(ticket.id(), mentor, "respuesta legitima"));
        UserId guia = UserId.of(UUID.randomUUID());
        actores.conActor(guia, UserRole.TRAINEE_GUIDE);
        acompanamiento.acompana(guia, aprendizAjeno());

        assertThatThrownBy(() -> service.responder(new ResponderTicketMentorCommand(ticket.id(), guia, "ajena")))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.guardar(new GuardarEnBibliotecaCommand(ticket.id(), guia)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    // ── Spec 056 (FR-029, FR-030): el líder de la formación responde ─────────

    // spec 056 FR-030
    @Test
    @DisplayName("spec 056 FR-030: el líder de la formación del aprendiz responde su consulta; queda quién respondió y el aprendiz recibe el aviso")
    void elLiderDeLaFormacionResponde() {
        TicketMentor ticket = abrirTicketDeTrainee();

        TicketConAutor respondido = service.responder(new ResponderTicketMentorCommand(ticket.id(), lider, "Prueba con una sola cosa"));

        assertThat(respondido.ticket().estado()).isEqualTo(EstadoTicketMentor.RESPONDIDO);
        assertThat(respondido.ticket().respondidoPor()).as("queda el líder, no el mentor de hoy del aprendiz").isEqualTo(lider);
        assertThat(respondido.nombreDeQuienRespondio()).isEqualTo("Actor de prueba");
        assertThat(events.eventosPublicados()).last().isInstanceOfSatisfying(TicketMentorRespondidoEvent.class,
                evento -> assertThat(evento.participanteId()).isEqualTo(trainee));
    }

    // spec 056 FR-030
    @Test
    @DisplayName("spec 056 FR-030: el líder no responde la consulta de un aprendiz de otra formación: 403 FUERA_DE_SU_ALCANCE y el ticket sigue abierto")
    void elLiderNoRespondeFueraDeSuFormacion() {
        TicketMentor ticket = abrirTicketDeTrainee();
        formacionDelAprendiz = Formacion.DIPLOMADO;

        assertThatThrownBy(() -> service.responder(new ResponderTicketMentorCommand(ticket.id(), lider, "No me toca")))
                .isInstanceOfSatisfying(NotAuthorizedException.class, e -> assertThat(e.codigo()).isEqualTo(NotAuthorizedException.FUERA_DE_SU_ALCANCE));

        assertThat(port.byId(ticket.id()).orElseThrow().estado()).isEqualTo(EstadoTicketMentor.ABIERTO);
    }

    // spec 056 FR-030
    @Test
    @DisplayName("spec 056 FR-030: un líder suspendido no responde, y responder un ticket que no existe es 404")
    void unLiderSuspendidoNoResponde() {
        TicketMentor ticket = abrirTicketDeTrainee();

        assertThatThrownBy(() -> service.responder(new ResponderTicketMentorCommand(ticket.id(), liderSuspendido, "Hola")))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.responder(new ResponderTicketMentorCommand(TicketMentorId.of(UUID.randomUUID()), lider, "Hola")))
                .isInstanceOf(NoSuchElementException.class);
    }

    // spec 056 FR-030
    @Test
    @DisplayName("spec 056 FR-030: lo que el líder abrió es solo responder: guardar en la biblioteca sigue siendo del mentor asignado, y Administración sigue sin responder")
    void guardarYAdministracionNoCambian() {
        TicketMentor ticket = abrirTicketDeTrainee();

        assertThatThrownBy(() -> service.responder(new ResponderTicketMentorCommand(ticket.id(), admin, "No soy el mentor"))).isInstanceOf(NotAuthorizedException.class);
        service.responder(new ResponderTicketMentorCommand(ticket.id(), lider, "Respuesta del líder"));

        assertThatThrownBy(() -> service.guardar(new GuardarEnBibliotecaCommand(ticket.id(), lider))).isInstanceOf(NotAuthorizedException.class);
    }

    // spec 056 FR-030
    @Test
    @DisplayName("spec 056 FR-030: lo que se escribe se lee: las listas traen el nombre de quien respondió cada consulta, en una página sola, y vacío en las abiertas")
    void laListaTraeElNombreDeQuienRespondio() {
        TicketMentor respondida = abrirTicketDeTrainee();
        service.responder(new ResponderTicketMentorCommand(respondida.id(), lider, "Respuesta"));
        TicketMentor abierta = abrirTicketDeTrainee();

        var pagina = service.todos(lider, null);

        assertThat(pagina.tickets()).extracting(TicketMentor::id).contains(respondida.id(), abierta.id());
        assertThat(pagina.nombresDeQuienRespondio()).containsOnlyKeys(lider).containsEntry(lider, "Actor de prueba");
    }

    // N-1 (auditoría 20/11): la vista de plataforma del líder respeta la frontera de formación que ya exige responder()
    @Test
    @DisplayName("todos(): el líder ve las consultas de aprendices de su formación y no las de otra; ADMIN y ALCHEMIST ven las de todas")
    void elLiderNoVeLasConsultasDeOtraFormacion() {
        TicketMentor deLaEscuela = abrirTicketDeTrainee();
        UserId deOtraFormacion = aprendizAjeno();
        Formacion otra = java.util.Arrays.stream(Formacion.values()).filter(f -> f != Formacion.ESCUELA).findFirst().orElseThrow();
        formacionDeCadaAprendiz.put(deOtraFormacion, otra);
        TicketMentor ajeno = service.abrir(new AbrirTicketMentorCommand(deOtraFormacion, "Bloqueo ajeno", "Solucion ajena", "Impacto ajeno"));
        UserId alquimista = UserId.of(UUID.randomUUID());
        actores.conActor(alquimista, UserRole.ALCHEMIST);

        assertThat(service.todos(lider, null).tickets()).extracting(TicketMentor::id).containsExactly(deLaEscuela.id());
        assertThat(service.todos(admin, null).tickets()).extracting(TicketMentor::id).containsExactlyInAnyOrder(deLaEscuela.id(), ajeno.id());
        assertThat(service.todos(alquimista, null).tickets()).extracting(TicketMentor::id).containsExactlyInAnyOrder(deLaEscuela.id(), ajeno.id());
    }

    @Test
    @DisplayName("todos(): el filtro del líder no rompe la paginación por cursor: 35 propias entre 150 ajenas salen en dos páginas sin repetir ni perder")
    void laPaginacionDelLiderSigueFuncionandoConElFiltro() {
        // reloj que avanza un segundo por lectura: el cursor es por creadoEn y con el reloj fijo todas serían iguales
        java.util.concurrent.atomic.AtomicLong segundos = new java.util.concurrent.atomic.AtomicLong();
        com.renaser.os.shared.domain.Clock reloj = new com.renaser.os.shared.domain.Clock() {
            @Override
            public Instant now() {
                return CLOCK.now().plusSeconds(segundos.incrementAndGet());
            }

            @Override
            public java.time.LocalDate today() {
                return CLOCK.today();
            }
        };
        service = new TicketMentorService(port, port, port, actores, participaciones, acompanamiento, alcanceDelLider(), events, reloj,
                idsSecuenciales());
        UserId deOtraFormacion = aprendizAjeno();
        Formacion otra = java.util.Arrays.stream(Formacion.values()).filter(f -> f != Formacion.ESCUELA).findFirst().orElseThrow();
        formacionDeCadaAprendiz.put(deOtraFormacion, otra);
        java.util.List<com.renaser.os.support.domain.model.ticketmentor.TicketMentorId> propios = new java.util.ArrayList<>();
        for (int i = 0; i < 150; i++) {
            service.abrir(new AbrirTicketMentorCommand(deOtraFormacion, "Ajeno " + i, "S", "I"));
            if (i % 4 == 0 && propios.size() < 35) {
                propios.add(abrirTicketDeTrainee().id());
            }
        }
        while (propios.size() < 35) {
            propios.add(abrirTicketDeTrainee().id());
        }

        var primera = service.todos(lider, null);
        assertThat(primera.tickets()).hasSize(30);
        assertThat(primera.nextCursor()).isNotNull();
        var segunda = service.todos(lider, primera.nextCursor());
        assertThat(segunda.tickets()).hasSize(5);
        java.util.List<com.renaser.os.support.domain.model.ticketmentor.TicketMentorId> vistos = new java.util.ArrayList<>();
        primera.tickets().forEach(t -> vistos.add(t.id()));
        segunda.tickets().forEach(t -> vistos.add(t.id()));
        assertThat(vistos).containsExactlyInAnyOrderElementsOf(propios).doesNotHaveDuplicates();
    }
}
