package com.renaser.os.support.application.services;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.infrastructure.storage.NoOpAlmacenamientoAdapter;
import com.renaser.os.support.application.ports.in.ticketsoporte.AbrirTicketSoporteUseCase.AbrirTicketSoporteCommand;
import com.renaser.os.support.application.ports.in.ticketsoporte.ResolverTicketSoporteUseCase.ResolverTicketSoporteCommand;
import com.renaser.os.support.application.ports.in.ticketsoporte.SolicitarUrlAdjuntoSoporteUseCase.SolicitarUrlAdjuntoCommand;
import com.renaser.os.support.application.ports.in.ticketsoporte.TicketSoporteVista;
import com.renaser.os.support.domain.model.ticketsoporte.CategoriaSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.EstadoTicketSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.TicketSoporteId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketSoporteServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private InMemoryTicketSoportePort port;
    private FakeUserSummaryFinder actores;
    private TicketSoporteService service;

    private UserId trainee;
    private UserId admin;
    private UserId mentor;

    @BeforeEach
    void setUp() {
        port = new InMemoryTicketSoportePort();
        actores = new FakeUserSummaryFinder();
        service = new TicketSoporteService(port, port, actores, new NoOpAlmacenamientoAdapter(), CLOCK,
                idsSecuenciales());

        trainee = UserId.of(UUID.randomUUID());
        admin = UserId.of(UUID.randomUUID());
        mentor = UserId.of(UUID.randomUUID());
        actores.conActor(trainee, UserRole.TRAINEE).conActor(admin, UserRole.ADMIN).conActor(mentor, UserRole.MENTOR);
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

    @Test
    @DisplayName("abrir(): cualquier autenticado puede abrir un ticket, sin categoria explicita defaultea a OTRO")
    void abrirSinCategoriaDefaulteaAOtro() {
        TicketSoporteVista vista = service.abrir(new AbrirTicketSoporteCommand(trainee, null, "Asunto",
                "Un mensaje con longitud suficiente", null, null, null));

        assertThat(vista.ticket().categoria()).isEqualTo(CategoriaSoporte.OTRO);
        assertThat(vista.ticket().estado()).isEqualTo(EstadoTicketSoporte.ABIERTO);
    }

    @Test
    @DisplayName("abrir(): respeta la categoria cuando el cliente la manda")
    void abrirConCategoriaExplicita() {
        TicketSoporteVista vista = service.abrir(new AbrirTicketSoporteCommand(trainee, CategoriaSoporte.FACTURACION,
                "Cobro duplicado", "Me cobraron dos veces la suscripcion mensual", null, null, null));

        assertThat(vista.ticket().categoria()).isEqualTo(CategoriaSoporte.FACTURACION);
    }

    @Test
    @DisplayName("seguridad INVERSA: un actor SUSPENDED SI puede abrir un ticket de soporte (regla deliberada, docs/FEATURE_SUPPORT.md)")
    void suspendidoSiPuedeAbrirTicketDeSoporte() {
        UserId suspendido = UserId.of(UUID.randomUUID());
        actores.conActor(suspendido, UserRole.TRAINEE, UserStatus.SUSPENDED);

        TicketSoporteVista vista = service.abrir(new AbrirTicketSoporteCommand(suspendido, null, "Necesito ayuda",
                "No puedo acceder a mi cuenta suspendida, necesito hablar con alguien", null, null, null));

        assertThat(vista.ticket().usuarioId()).isEqualTo(suspendido);
    }

    @Test
    @DisplayName("seguridad INVERSA: un actor SUSPENDED SI puede ver su propio historial de tickets")
    void suspendidoSiPuedeVerSuHistorial() {
        UserId suspendido = UserId.of(UUID.randomUUID());
        actores.conActor(suspendido, UserRole.TRAINEE, UserStatus.SUSPENDED);
        service.abrir(new AbrirTicketSoporteCommand(suspendido, null, "Asunto",
                "Un mensaje con longitud suficiente", null, null, null));

        var propios = service.misTickets(suspendido);

        assertThat(propios).hasSize(1);
    }

    @Test
    @DisplayName("todos(): un ADMIN puede ver el inbox completo")
    void adminPuedeVerInboxCompleto() {
        service.abrir(new AbrirTicketSoporteCommand(trainee, null, "Asunto",
                "Un mensaje con longitud suficiente", null, null, null));

        var todos = service.todos(admin, null);

        assertThat(todos).hasSize(1);
    }

    @Test
    @DisplayName("seguridad: un MENTOR no tiene alcance sobre tickets de soporte (docs/FEATURE_SUPPORT.md: 'a MENTOR has no scope here at all')")
    void mentorNoPuedeVerInbox() {
        assertThatThrownBy(() -> service.todos(mentor, null)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("seguridad: un ADMIN SUSPENDED no puede administrar tickets de soporte")
    void adminSuspendidoNoPuedeAdministrar() {
        UserId adminSuspendido = UserId.of(UUID.randomUUID());
        actores.conActor(adminSuspendido, UserRole.ADMIN, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> service.todos(adminSuspendido, null)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("resolver(): un ADMIN puede resolver un ticket abierto")
    void adminPuedeResolver() {
        TicketSoporteId id = service.abrir(new AbrirTicketSoporteCommand(trainee, null, "Asunto",
                "Un mensaje con longitud suficiente", null, null, null)).ticket().id();

        TicketSoporteVista resuelto = service.resolver(new ResolverTicketSoporteCommand(id, admin,
                "Reinstalar soluciona el problema"));

        assertThat(resuelto.ticket().estado()).isEqualTo(EstadoTicketSoporte.RESUELTO);
        assertThat(resuelto.ticket().notasAdmin()).isEqualTo("Reinstalar soluciona el problema");
    }

    @Test
    @DisplayName("seguridad: un MENTOR no puede resolver un ticket de soporte")
    void mentorNoPuedeResolver() {
        TicketSoporteId id = service.abrir(new AbrirTicketSoporteCommand(trainee, null, "Asunto",
                "Un mensaje con longitud suficiente", null, null, null)).ticket().id();

        assertThatThrownBy(() -> service.resolver(new ResolverTicketSoporteCommand(id, mentor, "notas")))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("resolver() sobre un ticket inexistente lanza NoSuchElementException")
    void resolverTicketInexistente() {
        assertThatThrownBy(() -> service.resolver(new ResolverTicketSoporteCommand(
                TicketSoporteId.of(UUID.randomUUID()), admin, "notas")))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("solicitar(): la ruta lleva el prefijo soporte/{usuarioId}/ y conserva la extension del archivo")
    void solicitarUrlDeAdjuntoUsaElPrefijoCorrecto() {
        var resultado = service.solicitar(new SolicitarUrlAdjuntoCommand(trainee, "captura.png", "image/png"));

        assertThat(resultado.ruta()).startsWith("soporte/" + trainee.value());
        assertThat(resultado.ruta()).endsWith(".png");
        assertThat(resultado.urlSubida()).isNotNull();
    }

    @Test
    @DisplayName("la URL de lectura del adjunto se firma al leer, nunca se persiste (D-34)")
    void laUrlDeLecturaSeFirmaAlLeerNoAlGuardar() {
        var upload = service.solicitar(new SolicitarUrlAdjuntoCommand(trainee, "captura.png", "image/png"));

        TicketSoporteVista vista = service.abrir(new AbrirTicketSoporteCommand(trainee, null, "Con adjunto",
                "Mando una captura de pantalla del error", null, upload.bucket(), upload.ruta()));

        assertThat(vista.attachmentUrl()).isNotNull();
        assertThat(vista.ticket().adjunto().ruta()).isEqualTo(upload.ruta());
    }

    @Test
    @DisplayName("un ticket sin adjunto no tiene attachmentUrl")
    void ticketSinAdjuntoNoTieneUrl() {
        TicketSoporteVista vista = service.abrir(new AbrirTicketSoporteCommand(trainee, null, "Sin adjunto",
                "No mando ninguna captura de pantalla", null, null, null));

        assertThat(vista.attachmentUrl()).isNull();
    }
}
