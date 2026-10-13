package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.habitoadmin.ActualizarHabitoUseCase.ActualizarHabitoCommand;
import com.renaser.os.habits.application.ports.in.habitoadmin.CambiarActivoHabitoUseCase.CambiarActivoHabitoCommand;
import com.renaser.os.habits.application.ports.in.habitoadmin.CrearHabitoUseCase.CrearHabitoCommand;
import com.renaser.os.habits.application.ports.in.habitoadmin.EliminarHabitoUseCase.EliminarHabitoCommand;
import com.renaser.os.habits.application.ports.out.audiencia.ContarRegistrosDeHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.SaveHabitoPort;
import com.renaser.os.habits.application.ports.in.habitoadmin.ConsultarRolesElegiblesUseCase.RolElegible;
import com.renaser.os.habits.domain.model.audiencia.Audiencia;
import com.renaser.os.habits.domain.model.audiencia.CodigoDeRol;
import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HabitoAdminServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-26T10:00:00Z"));
    /** Identidad fija: con el id entrando por el puerto IdGenerator, crearDeSistema() ya no lo sortea. */
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Mock
    private LoadHabitoPort loadPort;
    @Mock
    private SaveHabitoPort savePort;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private ContarRegistrosDeHabitoPort registrosPort;
    private final AudienciasEnMemoria audiencias = new AudienciasEnMemoria();

    private HabitoAdminService service;

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId alchemist = UserId.of(UUID.randomUUID());
    private final UserId mentor = UserId.of(UUID.randomUUID());
    private final UserId suspendedAdmin = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new HabitoAdminService(loadPort, savePort, new HabitoAdminGuard(userSummaryFinder), CLOCK,
                idGenerator, new AudienciasDelCatalogo(audiencias, audiencias, userSummaryFinder, CLOCK), registrosPort);
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(userSummaryFinder.findById(admin))
                .thenReturn(Optional.of(new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(alchemist)).thenReturn(
                Optional.of(new UserSummary(alchemist, "Alquimista", null, UserRole.ALCHEMIST, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(mentor))
                .thenReturn(Optional.of(new UserSummary(mentor, "Mentor", null, UserRole.MENTOR, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(suspendedAdmin)).thenReturn(Optional.of(
                new UserSummary(suspendedAdmin, "Admin suspendido", null, UserRole.ADMIN, UserStatus.SUSPENDED)));
        lenient().when(savePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static DetallesHabito detalles() {
        return new DetallesHabito("desc", "CUERPO", ExigenciaEvidencia.OPCIONAL, false, false);
    }

    @Test
    void crearComoMentorEsRechazado() {
        var command = new CrearHabitoCommand(mentor, "Titulo", TipoHabito.CHECKBOX, detalles());
        assertThatThrownBy(() -> service.crear(command)).isInstanceOf(NotAuthorizedException.class);
        verify(savePort, never()).save(any());
    }

    @Test
    void crearComoAdminSuspendidoEsRechazado() {
        var command = new CrearHabitoCommand(suspendedAdmin, "Titulo", TipoHabito.CHECKBOX, detalles());
        assertThatThrownBy(() -> service.crear(command)).isInstanceOf(NotAuthorizedException.class);
        verify(savePort, never()).save(any());
    }

    @Test
    void crearComoAlchemistFunciona() {
        var command = new CrearHabitoCommand(alchemist, "Titulo", TipoHabito.CHECKBOX, detalles());

        Habito creado = service.crear(command).habito();

        assertThat(creado.titulo()).isEqualTo("Titulo");
        assertThat(creado.esDeSistema()).isTrue();
        assertThat(creado.claveSistema()).isNull();
        verify(savePort).save(any());
    }

    @Test
    void actualizarSobreHabitoInexistenteFalla404() {
        HabitoId id = HabitoId.of(UUID.randomUUID());
        when(loadPort.byId(id)).thenReturn(Optional.empty());
        var command = new ActualizarHabitoCommand(admin, id, detalles(), false);

        assertThatThrownBy(() -> service.actualizar(command)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void actualizarNoCambiaElTipoNiLaClaveSistema() {
        Habito existente = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Titulo", TipoHabito.BLOQUEO,
                detalles(), CLOCK.now());
        HabitoId id = existente.id();
        when(loadPort.byId(id)).thenReturn(Optional.of(existente));
        var nuevosDetalles = new DetallesHabito("otra", "MENTE", ExigenciaEvidencia.OBLIGATORIA, true, true);

        Habito actualizado = service.actualizar(new ActualizarHabitoCommand(admin, id, nuevosDetalles, false)).habito();

        assertThat(actualizado.tipo()).isEqualTo(TipoHabito.BLOQUEO);
        assertThat(actualizado.claveSistema()).isNull();
        assertThat(actualizado.categoriaClave()).isEqualTo("MENTE");
    }

    // ── E-263: la bandera de intoxicación no se apaga en silencio ───────────

    @Test
    void editarSinInformarLaBanderaDeIntoxicacionLaConserva() {
        Habito post = postConBandera(true);
        when(loadPort.byId(post.id())).thenReturn(Optional.of(post));
        when(savePort.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        // El pedido no la trajo: los detalles dicen false, pero no se aplica.
        var otraDescripcion = new DetallesHabito("Escribe solo lo bueno", "CUERPO", ExigenciaEvidencia.OPCIONAL,
                false, false);

        Habito editado = service.actualizar(new ActualizarHabitoCommand(admin, post.id(), otraDescripcion, true)).habito();

        assertThat(editado.obligatorioEnIntoxicacion()).isTrue();
        assertThat(editado.descripcion()).isEqualTo("Escribe solo lo bueno");
    }

    @Test
    void editarConLaBanderaEnFalseExplicitoLaApaga() {
        Habito post = postConBandera(true);
        when(loadPort.byId(post.id())).thenReturn(Optional.of(post));
        when(savePort.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        Habito editado = service.actualizar(new ActualizarHabitoCommand(admin, post.id(), detalles(), false)).habito();

        assertThat(editado.obligatorioEnIntoxicacion()).isFalse();
    }

    @Test
    void editarConLaBanderaEnTrueExplicitoLaPrende() {
        Habito post = postConBandera(false);
        when(loadPort.byId(post.id())).thenReturn(Optional.of(post));
        when(savePort.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        var conBandera = new DetallesHabito("desc", "CUERPO", ExigenciaEvidencia.OPCIONAL, false, true);

        Habito editado = service.actualizar(new ActualizarHabitoCommand(admin, post.id(), conBandera, false)).habito();

        assertThat(editado.obligatorioEnIntoxicacion()).isTrue();
    }

    private Habito postConBandera(boolean obligatorioEnIntoxicacion) {
        return Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "POST DIARIO EN COMUNIDAD", TipoHabito.CHECKBOX,
                new DetallesHabito("desc", "CUERPO", ExigenciaEvidencia.OPCIONAL, false, obligatorioEnIntoxicacion),
                CLOCK.now());
    }

    @Test
    void cambiarActivoDesactivaYReactiva() {
        Habito existente = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Titulo", TipoHabito.CHECKBOX,
                detalles(), CLOCK.now());
        HabitoId id = existente.id();
        when(loadPort.byId(id)).thenReturn(Optional.of(existente));

        Habito desactivado = service.cambiarActivo(new CambiarActivoHabitoCommand(admin, id, false)).habito();
        assertThat(desactivado.activo()).isFalse();

        Habito reactivado = service.cambiarActivo(new CambiarActivoHabitoCommand(admin, id, true)).habito();
        assertThat(reactivado.activo()).isTrue();
    }

    @Test
    void eliminarComoMentorEsRechazadoSinTocarElPuerto() {
        var command = new EliminarHabitoCommand(mentor, HabitoId.of(UUID.randomUUID()));
        assertThatThrownBy(() -> service.eliminar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(savePort, never()).eliminar(any());
    }

    @Test
    void eliminarSobreHabitoInexistenteFalla404() {
        HabitoId id = HabitoId.of(UUID.randomUUID());
        when(loadPort.byId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminar(new EliminarHabitoCommand(admin, id)))
                .isInstanceOf(NoSuchElementException.class);
        verify(savePort, never()).eliminar(any());
    }

    @Test
    void listarComoMentorEsRechazado() {
        assertThatThrownBy(() -> service.listar(mentor)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void listarComoAdminDevuelveElCatalogoCompleto() {
        when(loadPort.catalogoCompleto()).thenReturn(
                java.util.List.of(Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "A", TipoHabito.CHECKBOX,
                        detalles(), CLOCK.now())));

        assertThat(service.listar(admin)).hasSize(1);
    }
    // ── Spec 001 (D-264): audiencia por rol ────────────────────────────────

    private static final Audiencia SOLO_MENTORES = Audiencia.de(Set.of(CodigoDeRol.of("MENTOR")));

    private Habito existenteDeSistema() {
        Habito existente = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Titulo", TipoHabito.CHECKBOX,
                detalles(), CLOCK.now());
        lenient().when(loadPort.byId(existente.id())).thenReturn(Optional.of(existente));
        return existente;
    }

    @Test
    @DisplayName("crear con roles guarda la primera versión, que rige desde la creación")
    void crearConRolesGuardaLaPrimeraVersion() {
        var creado = service.crear(new CrearHabitoCommand(admin, "Revisión del grupo", TipoHabito.CHECKBOX, detalles(),
                SOLO_MENTORES));

        assertThat(creado.audienciaGuardada()).isEqualTo(SOLO_MENTORES);
        assertThat(creado.rigeDesdeManana()).as("la audiencia con la que nace rige desde ya").isFalse();
        assertThat(audiencias.versiones).singleElement()
                .satisfies(v -> assertThat(v.rigeDesde()).isEqualTo(CLOCK.now()));
    }

    @Test
    @DisplayName("crear para todos no guarda versión: no tener versiones ya es «para todos»")
    void crearParaTodosNoGuardaVersion() {
        var creado = service.crear(new CrearHabitoCommand(admin, "Para todos", TipoHabito.CHECKBOX, detalles()));

        assertThat(creado.audienciaGuardada().paraTodos()).isTrue();
        assertThat(audiencias.versiones).isEmpty();
    }

    @Test
    @DisplayName("restringir un hábito sin versiones anota antes la base «para todos» desde siempre")
    void primerCambioAnotaLaVersionBase() {
        Habito existente = existenteDeSistema();

        service.actualizar(new ActualizarHabitoCommand(admin, existente.id(), detalles(), true, SOLO_MENTORES));

        assertThat(audiencias.versiones).hasSize(2);
        assertThat(audiencias.versiones.getFirst().audiencia()).isEqualTo(Audiencia.PARA_TODOS);
        assertThat(audiencias.versiones.getFirst().rigeDesde()).isEqualTo(AudienciasDelCatalogo.VERSION_BASE);
        assertThat(audiencias.versiones.getLast().audiencia()).isEqualTo(SOLO_MENTORES);
        assertThat(audiencias.versiones.getLast().cambiadoPor()).isEqualTo(admin);
        when(loadPort.catalogoCompleto()).thenReturn(List.of(existente));
        assertThat(service.listar(admin).stream().filter(h -> h.habito().id().equals(existente.id())))
                .singleElement().satisfies(h -> assertThat(h.rigeDesdeManana()).isTrue());
    }

    @Test
    @DisplayName("editar con la misma audiencia no crea historia; sin targetRoles no se toca")
    void editarSinCambioDeAudienciaNoCreaVersion() {
        Habito existente = existenteDeSistema();
        service.actualizar(new ActualizarHabitoCommand(admin, existente.id(), detalles(), true, SOLO_MENTORES));
        int antes = audiencias.versiones.size();

        service.actualizar(new ActualizarHabitoCommand(admin, existente.id(), detalles(), true, SOLO_MENTORES));
        service.actualizar(new ActualizarHabitoCommand(admin, existente.id(), detalles(), true));

        assertThat(audiencias.versiones).hasSize(antes);
    }

    @Test
    @DisplayName("un rol que no existe se rechaza con 400 y no se guarda nada")
    void rolDesconocidoSeRechaza() {
        var command = new CrearHabitoCommand(admin, "X", TipoHabito.CHECKBOX, detalles(),
                Audiencia.de(Set.of(CodigoDeRol.of("COACH"))));

        assertThatThrownBy(() -> service.crear(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COACH");
        verify(savePort, never()).save(any());
        assertThat(audiencias.versiones).isEmpty();
    }

    @Test
    @DisplayName("listar por rol incluye los «para todos» y los de ese rol")
    void listarPorRol() {
        Habito paraTodos = existenteDeSistema();
        Habito deMentores = existenteDeSistema();
        when(loadPort.catalogoCompleto()).thenReturn(List.of(paraTodos, deMentores));
        service.actualizar(new ActualizarHabitoCommand(admin, deMentores.id(), detalles(), true, SOLO_MENTORES));

        assertThat(service.listar(admin, CodigoDeRol.of("TRAINEE"))).extracting(h -> h.habito().id())
                .containsExactly(paraTodos.id());
        assertThat(service.listar(admin, CodigoDeRol.of("MENTOR"))).hasSize(2);
    }

    @Test
    @DisplayName("los roles elegibles salen del sistema, del aprendiz al alquimista, con su nombre")
    void rolesElegibles() {
        assertThat(service.rolesElegibles(admin)).extracting(RolElegible::codigo)
                .containsExactly("TRAINEE", "TRAINEE_GUIDE", "MENTOR", "MENTOR_LEAD", "ADMIN", "ALCHEMIST");
        assertThat(service.rolesElegibles(admin).get(1).nombre()).isEqualTo("Guía de aprendiz");
        assertThat(service.rolesElegibles(admin).get(3).nombre()).isEqualTo("Líder de mentores");
        assertThatThrownBy(() -> service.rolesElegibles(mentor)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("eliminar un hábito con registros se rechaza con el motivo, sin tocar la base")
    void eliminarConHistoriaSeRechaza() {
        Habito existente = existenteDeSistema();
        when(registrosPort.tieneRegistros(existente.id())).thenReturn(true);

        assertThatThrownBy(() -> service.eliminar(new EliminarHabitoCommand(admin, existente.id())))
                .isInstanceOf(IllegalStateException.class).hasMessage(HabitoAdminService.CON_HISTORIA);
        verify(savePort, never()).eliminar(any());
    }

    @Test
    @DisplayName("un hábito personal no existe para el panel del catálogo")
    void habitoPersonalEs404() {
        Habito personal = Habito.crearPersonal(HabitoId.of(UUID.randomUUID()), mentor, "Mío", TipoHabito.CHECKBOX,
                "CUERPO", null, null, CLOCK.now());
        when(loadPort.byId(personal.id())).thenReturn(Optional.of(personal));

        assertThatThrownBy(() -> service.cambiarActivo(new CambiarActivoHabitoCommand(admin, personal.id(), false)))
                .isInstanceOf(NoSuchElementException.class);
    }
}
