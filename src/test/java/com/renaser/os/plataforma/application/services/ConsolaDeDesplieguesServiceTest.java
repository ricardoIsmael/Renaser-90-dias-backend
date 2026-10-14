package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.api.FuncionalidadCambiadaEvent;
import com.renaser.os.plataforma.application.ports.in.AccesoDeConsola;
import com.renaser.os.plataforma.application.ports.in.CambiarDespliegueUseCase.CambiarDespliegueCommand;
import com.renaser.os.plataforma.application.ports.in.DespliegueEnConsola;
import com.renaser.os.plataforma.application.ports.out.MetricasDeDesplieguePort;
import com.renaser.os.plataforma.domain.model.auditoria.CambioAuditado;
import com.renaser.os.plataforma.domain.model.auditoria.TipoDeCambio;
import com.renaser.os.plataforma.domain.model.despliegue.DespliegueCambiado;
import com.renaser.os.plataforma.domain.model.estado.ReglaDeDisponibilidad;
import com.renaser.os.shared.application.EstadoAutomaticoDeFuncionalidades;
import com.renaser.os.shared.domain.ConsolaBloqueadaException;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.ReautenticacionRequeridaException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CorreosDeUsuariosFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El escalón de un despliegue desde la consola (contrato fase 2 §3.4): cerrojos, versión, crítico y lo que deja. */
@SuppressWarnings("unchecked")
class ConsolaDeDesplieguesServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-14T03:30:00Z"));
    private static final String SESION = "sesion-1";

    private final UserId soporte = UserId.of(UUID.randomUUID());
    private final UserId admin = UserId.of(UUID.randomUUID());
    private final Dobles.Despliegues despliegues = new Dobles.Despliegues();
    private final Dobles.Auditoria auditoria = new Dobles.Auditoria();
    private final Dobles.Tokens tokens = new Dobles.Tokens();
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private ConsolaDeDesplieguesService service;
    private String consola;

    @BeforeEach
    void armar() {
        when(usuarios.findById(soporte)).thenReturn(Optional.of(
                new UserSummary(soporte, "Dueño", null, UserRole.IT_SUPPORT, UserStatus.ACTIVE)));
        when(usuarios.findById(admin)).thenReturn(Optional.of(
                new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        when(usuarios.findByIds(anyCollection())).thenReturn(Map.of(soporte,
                new UserSummary(soporte, "Dueño", null, UserRole.IT_SUPPORT, UserStatus.ACTIVE)));
        MetricasDeDesplieguePort metricas = mock(MetricasDeDesplieguePort.class);
        when(metricas.desde(any(), any())).thenReturn(List.of());
        CorreosDeUsuariosFinder correos = mock(CorreosDeUsuariosFinder.class);
        when(correos.correosDe(anyCollection())).thenReturn(Map.of());
        GuardiaDeSoporteTi guardia = new GuardiaDeSoporteTi(usuarios, tokens, Duration.ofMinutes(15));
        service = new ConsolaDeDesplieguesService(guardia, estadoVigente(), despliegues, despliegues, tokens,
                new AnotadorDeDespliegues(auditoria, events, UUID::randomUUID),
                new DesplieguesEnConsola(despliegues, metricas, usuarios, correos, CLOCK), CLOCK);
        consola = tokens.emitirDeConsola(soporte, SESION, Duration.ofMinutes(15));
    }

    static EstadoVigente estadoVigente() {
        ObjectProvider<EstadoAutomaticoDeFuncionalidades> sinAutomaticos = mock(ObjectProvider.class);
        when(sinAutomaticos.orderedStream()).thenReturn(Stream.empty());
        ObjectProvider<ReglaDeDisponibilidad> sinReglas = mock(ObjectProvider.class);
        when(sinReglas.orderedStream()).thenReturn(Stream.empty());
        return new EstadoVigente(new CopiaDeEstados(new Dobles.Base()), sinAutomaticos, sinReglas);
    }

    @Test
    @DisplayName("un ADMIN no mueve un despliegue (403) y Soporte TI sin desbloquear tampoco (423)")
    void cerrojos() {
        assertThatThrownBy(() -> service.cambiar(pedido(admin, consola, "eventos", 5, null, null)))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.cambiar(pedido(soporte, null, "eventos", 5, null, null)))
                .isInstanceOf(ConsolaBloqueadaException.class);
        assertThatThrownBy(() -> service.detalle(new AccesoDeConsola(admin, consola, SESION), "eventos"))
                .isInstanceOf(NotAuthorizedException.class);
        assertThat(despliegues.filas).isEmpty();
    }

    @Test
    @DisplayName("400: clave fuera del catálogo (anti-bloqueo), sin porcentaje, fuera de rango o sin motivo")
    void pedidosInvalidos() {
        assertThatThrownBy(() -> service.cambiar(pedido(soporte, consola, "login", 5, null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("catálogo");
        assertThatThrownBy(() -> service.cambiar(pedido(soporte, consola, "eventos", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.cambiar(pedido(soporte, consola, "eventos", 101, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.cambiar(new CambiarDespliegueCommand(acceso(soporte, consola), "eventos", 5,
                " ", null, null, null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.detalle(acceso(soporte, consola), "login"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("empezar al 5 % una no crítica: guarda, audita {despliegue: null} → {5 %, 0 probadores} y avisa")
    void empezar() {
        DespliegueEnConsola vista = service.cambiar(pedido(soporte, consola, "eventos", 5, 0L, null));

        assertThat(vista.enDespliegue()).isTrue();
        assertThat(vista.porcentaje()).isEqualTo(5);
        assertThat(vista.version()).isPositive();
        assertThat(vista.metricas()).hasSize(24);
        assertThat(vista.actualizadoPor().nombre()).isEqualTo("Dueño");
        CambioAuditado fila = auditoria.filas.getFirst();
        assertThat(fila.tipo()).isEqualTo(TipoDeCambio.MANUAL);
        assertThat(fila.clave()).isEqualTo("eventos");
        assertThat(fila.antes()).containsEntry("despliegue", null);
        assertThat(fila.despues()).isEqualTo(Map.of("despliegue", Map.of("porcentaje", 5, "probadores", 0)));
        assertThat(fila.motivo()).isEqualTo("Probar el calendario");
        FuncionalidadCambiadaEvent evento = evento(FuncionalidadCambiadaEvent.class);
        assertThat(evento.resumen()).isEqualTo("En despliegue al 5 %");
        assertThat(evento.critica()).isFalse();
        assertThat(evento.apagadaParaTodos()).isFalse();
        assertThat(evento(DespliegueCambiado.class).clave()).isEqualTo("eventos");
    }

    @Test
    @DisplayName("428 sin reautenticar: empezar al 0 % cualquiera, empezar o bajar una crítica; el token sirve una vez")
    void critico() {
        assertThatThrownBy(() -> service.cambiar(pedido(soporte, consola, "eventos", 0, null, null)))
                .as("al 0 % la esconde a todos").isInstanceOf(ReautenticacionRequeridaException.class);
        assertThatThrownBy(() -> service.cambiar(pedido(soporte, consola, "chat", 5, null, null)))
                .as("chat es crítica").isInstanceOf(ReautenticacionRequeridaException.class);
        assertThat(despliegues.filas).isEmpty();

        String token = tokens.emitirDeReautenticacion(soporte, SESION, Duration.ofMinutes(5));
        assertThat(service.cambiar(pedido(soporte, consola, "chat", 5, null, token)).porcentaje()).isEqualTo(5);
        assertThat(service.cambiar(pedido(soporte, consola, "chat", 25, null, null)).porcentaje())
                .as("subir no pide contraseña").isEqualTo(25);
        assertThatThrownBy(() -> service.cambiar(pedido(soporte, consola, "chat", 5, null, token)))
                .as("bajar una crítica, y el token ya se gastó").isInstanceOf(ReautenticacionRequeridaException.class);
        assertThat(service.cambiar(pedido(soporte, consola, "chat", 100, null, null)).enDespliegue())
                .as("terminar no pide contraseña").isFalse();
    }

    @Test
    @DisplayName("bajar una no crítica no pide contraseña; terminar borra la fila y audita {despliegue: null}")
    void bajarYTerminar() {
        service.cambiar(pedido(soporte, consola, "eventos", 50, null, null));
        service.cambiar(pedido(soporte, consola, "eventos", 25, null, null));

        DespliegueEnConsola terminado = service.cambiar(pedido(soporte, consola, "eventos", 100, null, null));

        assertThat(terminado.enDespliegue()).isFalse();
        assertThat(terminado.porcentaje()).isEqualTo(100);
        assertThat(terminado.version()).isZero();
        assertThat(despliegues.filas).isEmpty();
        assertThat(auditoria.filas).hasSize(3);
        assertThat(auditoria.filas.get(1).antes()).isEqualTo(Map.of("despliegue", Map.of("porcentaje", 50,
                "probadores", 0)));
        assertThat(auditoria.filas.get(2).despues()).containsEntry("despliegue", null);
        assertThat(evento(FuncionalidadCambiadaEvent.class).resumen()).startsWith("Terminó el despliegue");
    }

    @Test
    @DisplayName("409 con la versión vieja; pedir lo que ya está no guarda, no audita ni avisa")
    void versionYSinCambios() {
        long version = service.cambiar(pedido(soporte, consola, "eventos", 5, 0L, null)).version();

        assertThatThrownBy(() -> service.cambiar(pedido(soporte, consola, "eventos", 25, 0L, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.cambiar(pedido(soporte, consola, "eventos", 5, version, null)).version())
                .isEqualTo(version);
        assertThat(service.cambiar(pedido(soporte, consola, "radar", 100, null, null)).enDespliegue()).isFalse();
        assertThat(auditoria.filas).hasSize(1);
    }

    private CambiarDespliegueCommand pedido(UserId actor, String token, String clave, Integer porcentaje, Long version,
                                            String reautenticacion) {
        return new CambiarDespliegueCommand(acceso(actor, token), clave, porcentaje, "Probar el calendario", version,
                reautenticacion, "Prueba/1.0");
    }

    private static AccesoDeConsola acceso(UserId actor, String token) {
        return new AccesoDeConsola(actor, token, SESION);
    }

    private <T> T evento(Class<T> tipo) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events, atLeastOnce()).publishEvent(captor.capture());
        List<Object> todos = captor.getAllValues();
        return tipo.cast(todos.reversed().stream().filter(tipo::isInstance).findFirst()
                .orElseThrow(() -> new AssertionError("No se publicó " + tipo.getSimpleName())));
    }
}
