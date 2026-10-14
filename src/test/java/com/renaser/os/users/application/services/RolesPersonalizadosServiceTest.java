package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ConflictoDeRolPersonalizadoException;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.BorrarRolPersonalizadoCommand;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.CambioDeRolPersonalizado;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.CrearRolPersonalizadoCommand;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.EditarRolPersonalizadoCommand;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.ReemplazarRolPersonalizadoCommand;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.ReemplazoEnBloque;
import com.renaser.os.users.api.RolPersonalizadoCambiadoEvent;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.out.rolpersonalizado.CargarRolesPersonalizadosPort;
import com.renaser.os.users.application.ports.out.rolpersonalizado.GuardarRolesPersonalizadosPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La gestión de roles personalizados (D-269): el orden de los cerrojos y lo que deja cada operación. Sin Spring. */
class RolesPersonalizadosServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-14T15:00:00Z"));

    private final Base base = new Base();
    private final LoadUserPort usuarios = mock(LoadUserPort.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final UserId soporte = UserId.of(UUID.randomUUID());
    private final UserId admin = UserId.of(UUID.randomUUID());
    private RolesPersonalizadosService service;

    @BeforeEach
    void armar() {
        when(usuarios.byId(soporte)).thenReturn(Optional.of(cuenta(soporte, UserRole.IT_SUPPORT, UserStatus.ACTIVE)));
        when(usuarios.byId(admin)).thenReturn(Optional.of(cuenta(admin, UserRole.ADMIN, UserStatus.ACTIVE)));
        service = new RolesPersonalizadosService(base, base, usuarios, events, UUID::randomUUID, CLOCK);
    }

    private static User cuenta(UserId id, UserRole rol, UserStatus estado) {
        return User.rehydrate(id, new Email(id.value() + "@renaser.test"), rol, estado, "Cuenta", null, null, null,
                null);
    }

    private CambioDeRolPersonalizado crearMentorJunior() {
        return service.crear(new CrearRolPersonalizadoCommand(soporte, "Mentor junior", "Sin eventos",
                UserRole.MENTOR, Set.of("muro", "chat")));
    }

    @Test
    @DisplayName("solo Soporte TI activo gestiona: ADMIN o un Soporte TI suspendido reciben 403 sin escribir nada")
    void soloSoporteTi() {
        UserId suspendido = UserId.of(UUID.randomUUID());
        when(usuarios.byId(suspendido)).thenReturn(Optional.of(cuenta(suspendido, UserRole.IT_SUPPORT,
                UserStatus.SUSPENDED)));

        for (UserId actor : List.of(admin, suspendido)) {
            assertThatThrownBy(() -> service.crear(new CrearRolPersonalizadoCommand(actor, "Mentor junior", null,
                    UserRole.MENTOR, Set.of()))).isInstanceOf(NotAuthorizedException.class);
        }
        assertThat(base.roles).isEmpty();
        assertThat(base.bloqueos).isZero();
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("crear: bajo el candado, guarda el rol activo, avisa y devuelve la foto de después")
    void crear() {
        CambioDeRolPersonalizado cambio = crearMentorJunior();

        assertThat(cambio.antes()).isNull();
        assertThat(cambio.despues().nombre()).isEqualTo("Mentor junior");
        assertThat(cambio.despues().clave()).isEqualTo("rp:" + cambio.despues().id());
        assertThat(cambio.despues().funcionalidades()).containsExactlyInAnyOrder("muro", "chat");
        assertThat(base.bloqueos).isEqualTo(1);
        verify(events).publishEvent(new RolPersonalizadoCambiadoEvent(cambio.despues().id(), CLOCK.now()));
    }

    @Test
    @DisplayName("crear con un nombre repetido o el de un rol base, o pasando los 20 activos: 400")
    void crearRechaza() {
        crearMentorJunior();
        assertThatThrownBy(() -> service.crear(new CrearRolPersonalizadoCommand(soporte, "MENTOR JUNIOR", null,
                UserRole.TRAINEE, Set.of()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.crear(new CrearRolPersonalizadoCommand(soporte, "Líder de mentores", null,
                UserRole.MENTOR_LEAD, Set.of()))).isInstanceOf(IllegalArgumentException.class);
        for (int i = 1; i < 20; i++) {
            service.crear(new CrearRolPersonalizadoCommand(soporte, "Rol " + i, null, UserRole.TRAINEE, Set.of()));
        }
        assertThatThrownBy(() -> service.crear(new CrearRolPersonalizadoCommand(soporte, "Rol 21", null,
                UserRole.TRAINEE, Set.of()))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("20");
        assertThat(base.roles).hasSize(20);
    }

    @Test
    @DisplayName("editar con la versión vieja: 409; con la vigente, sube la versión y devuelve antes y después")
    void editarConVersion() {
        UUID id = crearMentorJunior().despues().id();

        assertThatThrownBy(() -> service.editar(new EditarRolPersonalizadoCommand(soporte, id, "Mentor junior", null,
                null, Set.of("muro"), true, 7))).isInstanceOf(ConflictoDeRolPersonalizadoException.class)
                .extracting(e -> ((ConflictoDeRolPersonalizadoException) e).codigo()).isEqualTo("VERSION_CAMBIADA");

        CambioDeRolPersonalizado cambio = service.editar(new EditarRolPersonalizadoCommand(soporte, id,
                "Mentor junior", null, null, Set.of("muro"), false, 1));

        assertThat(cambio.antes().activo()).isTrue();
        assertThat(cambio.antes().funcionalidades()).contains("chat");
        assertThat(cambio.despues().activo()).isFalse();
        assertThat(cambio.despues().version()).isEqualTo(2);
        assertThat(cambio.despues().funcionalidades()).containsExactly("muro");
    }

    @Test
    @DisplayName("reactivar cuenta para el cupo de 20; desactivar no")
    void reactivarCuentaParaElCupo() {
        UUID apagado = crearMentorJunior().despues().id();
        service.editar(new EditarRolPersonalizadoCommand(soporte, apagado, "Mentor junior", null, null, Set.of(),
                false, 1));
        for (int i = 1; i <= 20; i++) {
            service.crear(new CrearRolPersonalizadoCommand(soporte, "Rol " + i, null, UserRole.TRAINEE, Set.of()));
        }

        assertThatThrownBy(() -> service.editar(new EditarRolPersonalizadoCommand(soporte, apagado, "Mentor junior",
                null, null, Set.of(), true, 2))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("con personas: no cambia de base ni se borra (409); sin personas, se borra")
    void conPersonas() {
        UUID id = crearMentorJunior().despues().id();
        base.personas.put(id, 2L);

        assertThatThrownBy(() -> service.editar(new EditarRolPersonalizadoCommand(soporte, id, "Mentor junior", null,
                UserRole.TRAINEE_GUIDE, Set.of(), true, 1))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.borrar(new BorrarRolPersonalizadoCommand(soporte, id)))
                .isInstanceOf(IllegalStateException.class);

        base.personas.remove(id);
        CambioDeRolPersonalizado borrado = service.borrar(new BorrarRolPersonalizadoCommand(soporte, id));

        assertThat(borrado.despues()).isNull();
        assertThat(base.roles).doesNotContainKey(id);
    }

    @Test
    @DisplayName("un rol que no existe: 404 al editar, borrar o reemplazar")
    void noExiste() {
        UUID inventado = UUID.randomUUID();

        assertThatThrownBy(() -> service.borrar(new BorrarRolPersonalizadoCommand(soporte, inventado)))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.reemplazarEnBloque(new ReemplazarRolPersonalizadoCommand(soporte, inventado,
                null))).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("reemplazar en bloque: a otro rol con la misma base, o a ninguno; un destino inválido es 400")
    void reemplazar() {
        UUID origen = crearMentorJunior().despues().id();
        UUID otraBase = service.crear(new CrearRolPersonalizadoCommand(soporte, "Guía inicial", null,
                UserRole.TRAINEE_GUIDE, Set.of())).despues().id();
        UUID destino = service.crear(new CrearRolPersonalizadoCommand(soporte, "Mentor inicial", null,
                UserRole.MENTOR, Set.of())).despues().id();
        base.personas.put(origen, 3L);

        assertThatThrownBy(() -> service.reemplazarEnBloque(new ReemplazarRolPersonalizadoCommand(soporte, origen,
                otraBase))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.reemplazarEnBloque(new ReemplazarRolPersonalizadoCommand(soporte, origen,
                UUID.randomUUID()))).isInstanceOf(IllegalArgumentException.class);

        ReemplazoEnBloque reemplazo = service.reemplazarEnBloque(new ReemplazarRolPersonalizadoCommand(soporte,
                origen, destino));

        assertThat(reemplazo.personasMovidas()).isEqualTo(3);
        assertThat(reemplazo.origen().personas()).isEqualTo(3);
        assertThat(reemplazo.destino().id()).isEqualTo(destino);
        assertThat(base.personas).doesNotContainKey(origen).containsEntry(destino, 3L);
        verify(events, times(2).description("al crearlo y al reemplazarlo"))
                .publishEvent(new RolPersonalizadoCambiadoEvent(origen, CLOCK.now()));
    }

    /** La base en memoria: roles por id, personas por rol y cuántas veces se tomó el candado. */
    private static final class Base implements CargarRolesPersonalizadosPort, GuardarRolesPersonalizadosPort {

        final Map<UUID, RolPersonalizado> roles = new LinkedHashMap<>();
        final Map<UUID, Long> personas = new HashMap<>();
        int bloqueos;

        @Override
        public Optional<RolPersonalizado> porId(UUID id) {
            return Optional.ofNullable(roles.get(id));
        }

        @Override
        public List<RolPersonalizado> todos() {
            List<RolPersonalizado> todos = new ArrayList<>(roles.values());
            todos.sort(Comparator.comparing(RolPersonalizado::nombre));
            return todos;
        }

        @Override
        public Map<UUID, Long> personasPorRol() {
            return Map.copyOf(personas);
        }

        @Override
        public long personasCon(UUID rolId) {
            return personas.getOrDefault(rolId, 0L);
        }

        @Override
        public Map<UserId, UUID> asignacionesVigentes() {
            return Map.of();
        }

        @Override
        public Optional<UUID> vigenteDe(UserId usuario) {
            return Optional.empty();
        }

        @Override
        public void bloquearCambios() {
            bloqueos++;
        }

        @Override
        public void insertar(RolPersonalizado rol) {
            roles.put(rol.id(), rol);
        }

        @Override
        public boolean actualizar(RolPersonalizado rol, long versionEsperada) {
            if (roles.get(rol.id()).version() != versionEsperada) {
                return false;
            }
            roles.put(rol.id(), rol);
            return true;
        }

        @Override
        public void borrar(UUID id) {
            roles.remove(id);
        }

        @Override
        public int reemplazarAsignaciones(UUID origen, UUID destino) {
            long movidas = personas.getOrDefault(origen, 0L);
            personas.remove(origen);
            if (destino != null && movidas > 0) {
                personas.merge(destino, movidas, Long::sum);
            }
            return (int) movidas;
        }
    }
}
