package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.api.FuncionalidadCambiadaEvent;
import com.renaser.os.plataforma.application.ports.in.VisibilidadDelCodigoRenaser;
import com.renaser.os.plataforma.application.ports.out.CargarEstadosPort;
import com.renaser.os.plataforma.application.ports.out.GuardarEstadoPort;
import com.renaser.os.plataforma.domain.model.auditoria.CambioAuditado;
import com.renaser.os.plataforma.domain.model.catalogo.CodigoRenaserEnFichas;
import com.renaser.os.plataforma.domain.model.estado.EstadoDeFuncionalidad;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El interruptor «Código Renaser en las fichas» desde Administración (spec 034, D-299): quién, qué escribe y qué deja. Sin Spring. */
class VisibilidadDelCodigoRenaserServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-31T23:40:12Z"));

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId alquimista = UserId.of(UUID.randomUUID());
    private final UserId soporte = UserId.of(UUID.randomUUID());
    private final Dobles.Base base = new Dobles.Base();
    private final Dobles.Auditoria auditoria = new Dobles.Auditoria();
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private VisibilidadDelCodigoRenaserService service;

    @BeforeEach
    void armar() {
        usuario(admin, "Ana Pérez", UserRole.ADMIN, UserStatus.ACTIVE);
        usuario(alquimista, "Alba", UserRole.ALCHEMIST, UserStatus.ACTIVE);
        usuario(soporte, "Ricardo", UserRole.IT_SUPPORT, UserStatus.ACTIVE);
        service = new VisibilidadDelCodigoRenaserService(usuarios, base, base, auditoria, events, UUID::randomUUID, CLOCK);
    }

    @Test
    @DisplayName("de fábrica está apagado, sin último cambio")
    void deFabricaEstaApagado() {
        VisibilidadDelCodigoRenaser vista = service.consultar(admin);

        assertThat(vista.visible()).isFalse();
        assertThat(vista.mixto()).isFalse();
        assertThat(vista.ultimoCambio()).isNull();
        assertThat(base.filas).isEmpty();
    }

    @Test
    @DisplayName("Administración enciende: queda la fila (prendida para todos, sin excepciones), la auditoría completa y el evento que recarga las instancias")
    void enciende() {
        VisibilidadDelCodigoRenaser vista = service.cambiar(admin, true);

        assertThat(vista.visible()).isTrue();
        assertThat(vista.ultimoCambio().porNombre()).isEqualTo("Ana Pérez");
        assertThat(vista.ultimoCambio().en()).isEqualTo(CLOCK.now());
        EstadoDeFuncionalidad fila = base.filas.get("radar.fichas");
        assertThat(fila.prendidaGlobal()).isTrue();
        assertThat(fila.apagadaPara()).isEmpty();
        assertThat(fila.actualizadoPor()).isEqualTo(admin);

        assertThat(auditoria.filas).singleElement().satisfies(cambio -> {
            assertThat(cambio.clave()).isEqualTo("radar.fichas");
            assertThat(cambio.actor()).isEqualTo(admin);
            assertThat(cambio.motivo()).isEqualTo(CodigoRenaserEnFichas.MOTIVO_DEL_CAMBIO);
            assertThat(cambio.antes()).as("el «antes» es el valor de fábrica: apagado para los tres")
                    .containsEntry("apagadaPara", java.util.List.of("MENTOR", "MENTOR_LEAD", "TRAINEE_GUIDE"));
            assertThat(cambio.despues()).containsEntry("apagadaPara", java.util.List.of());
        });
        ArgumentCaptor<FuncionalidadCambiadaEvent> evento = ArgumentCaptor.forClass(FuncionalidadCambiadaEvent.class);
        verify(events).publishEvent(evento.capture());
        assertThat(evento.getValue().clave()).isEqualTo("radar.fichas");
        assertThat(evento.getValue().critica()).isFalse();
        assertThat(evento.getValue().apagadaParaTodos()).isFalse();
        assertThat(evento.getValue().actor()).isEqualTo(admin);
    }

    @Test
    @DisplayName("apagar de nuevo apaga a los tres con la funcionalidad prendida (Administración sigue viéndola) y deja otra fila de auditoría")
    void apagaDeNuevo() {
        service.cambiar(admin, true);
        VisibilidadDelCodigoRenaser vista = service.cambiar(alquimista, false);

        assertThat(vista.visible()).isFalse();
        assertThat(vista.ultimoCambio().porNombre()).isEqualTo("Alba");
        assertThat(base.filas.get("radar.fichas").prendidaGlobal()).isTrue();
        assertThat(base.filas.get("radar.fichas").apagadaPara())
                .containsExactlyInAnyOrder(UserRole.MENTOR, UserRole.TRAINEE_GUIDE, UserRole.MENTOR_LEAD);
        assertThat(auditoria.filas).hasSize(2).extracting(CambioAuditado::actor).containsExactly(admin, alquimista);
    }

    @Test
    @DisplayName("pedir lo que ya rige no escribe nada: ni fila, ni auditoría, ni evento (apagar de fábrica; encender dos veces)")
    void pedirLoVigenteNoEscribe() {
        service.cambiar(admin, false);
        assertThat(base.filas).isEmpty();
        assertThat(auditoria.filas).isEmpty();

        service.cambiar(admin, true);
        long version = base.filas.get("radar.fichas").version();
        service.cambiar(soporte, true);

        assertThat(base.filas.get("radar.fichas").version()).isEqualTo(version);
        assertThat(auditoria.filas).hasSize(1);
        verify(events).publishEvent(any(FuncionalidadCambiadaEvent.class));
    }

    @Test
    @DisplayName("Soporte TI lo decide también (034-4 B)")
    void soporteTiLoDecide() {
        assertThat(service.cambiar(soporte, true).visible()).isTrue();
        assertThat(service.consultar(soporte).visible()).isTrue();
    }

    @Test
    @DisplayName("estado mixto (Soporte TI apagó uno desde su consola): se lee «no lo ven todos» y Administración lo iguala")
    void mixto() {
        base.filas.put("radar.fichas", new EstadoDeFuncionalidad("radar.fichas", true, Set.of(UserRole.MENTOR), null,
                base.siguienteVersion(), soporte, CLOCK.now()));

        VisibilidadDelCodigoRenaser vista = service.consultar(admin);
        assertThat(vista.visible()).isFalse();
        assertThat(vista.mixto()).isTrue();
        assertThat(vista.porRol()).containsEntry(UserRole.MENTOR, false).containsEntry(UserRole.MENTOR_LEAD, true);

        assertThat(service.cambiar(admin, true).visible()).isTrue();
        assertThat(base.filas.get("radar.fichas").apagadaPara()).isEmpty();
    }

    @Test
    @DisplayName("dos administradores a la vez: gana el último, se relee y se reintenta; los dos cambios quedan en la auditoría")
    void ganaElUltimo() {
        // Otro cambio llega entre la lectura y la escritura: la primera escritura pierde la versión y se reintenta.
        var conCarrera = new BaseConCarrera(base);
        var servicio = new VisibilidadDelCodigoRenaserService(usuarios, conCarrera, conCarrera, auditoria, events,
                UUID::randomUUID, CLOCK);

        VisibilidadDelCodigoRenaser vista = servicio.cambiar(admin, true);

        assertThat(vista.visible()).isTrue();
        assertThat(base.filas.get("radar.fichas").actualizadoPor()).isEqualTo(admin);
        assertThat(auditoria.filas).extracting(CambioAuditado::actor).containsExactly(admin);
    }

    @Test
    @DisplayName("quien no es Administración, Alquimista ni Soporte TI recibe 403 al leer y al cambiar, y no se escribe nada")
    void otrosRoles() {
        for (UserRole rol : new UserRole[]{UserRole.TRAINEE, UserRole.MENTOR, UserRole.TRAINEE_GUIDE, UserRole.MENTOR_LEAD}) {
            UserId otro = UserId.of(UUID.randomUUID());
            usuario(otro, rol.name(), rol, UserStatus.ACTIVE);

            assertThatThrownBy(() -> service.consultar(otro)).as("leer como %s", rol).isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> service.cambiar(otro, true)).as("cambiar como %s", rol)
                    .isInstanceOf(NotAuthorizedException.class);
        }
        assertThat(base.filas).isEmpty();
        assertThat(auditoria.filas).isEmpty();
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("una cuenta suspendida o inexistente recibe 403 aunque su rol sea de Administración")
    void suspendidaOInexistente() {
        UserId suspendida = UserId.of(UUID.randomUUID());
        usuario(suspendida, "Suspendida", UserRole.ADMIN, UserStatus.SUSPENDED);
        UserId fantasma = UserId.of(UUID.randomUUID());
        when(usuarios.findById(fantasma)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiar(suspendida, true)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.consultar(suspendida)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.cambiar(fantasma, true)).isInstanceOf(NotAuthorizedException.class);
        assertThat(base.filas).isEmpty();
    }

    private void usuario(UserId id, String nombre, UserRole rol, UserStatus estado) {
        when(usuarios.findById(id)).thenReturn(Optional.of(new UserSummary(id, nombre, null, rol, estado)));
    }

    /** La base de siempre, pero otra administradora (la alquimista) deja «oculto» justo antes de la primera escritura. */
    private final class BaseConCarrera implements CargarEstadosPort, GuardarEstadoPort {

        private final Dobles.Base base;
        private boolean yaInterfirio;

        BaseConCarrera(Dobles.Base base) {
            this.base = base;
        }

        @Override
        public java.util.List<EstadoDeFuncionalidad> todos() {
            return base.todos();
        }

        @Override
        public long siguienteVersion() {
            return base.siguienteVersion();
        }

        @Override
        public boolean guardar(EstadoDeFuncionalidad nuevo, long versionEsperada) {
            if (!yaInterfirio) {
                yaInterfirio = true;
                base.guardar(new EstadoDeFuncionalidad("radar.fichas", true,
                        Set.of(UserRole.MENTOR, UserRole.TRAINEE_GUIDE, UserRole.MENTOR_LEAD), null,
                        base.siguienteVersion(), alquimista, CLOCK.now()), 0L);
            }
            return base.guardar(nuevo, versionEsperada);
        }
    }
}
