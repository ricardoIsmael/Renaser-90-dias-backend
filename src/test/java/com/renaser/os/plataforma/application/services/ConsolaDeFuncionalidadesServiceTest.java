package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.api.FuncionalidadCambiadaEvent;
import com.renaser.os.plataforma.application.ports.in.AccesoDeConsola;
import com.renaser.os.plataforma.application.ports.in.CambiarFuncionalidadUseCase.CambiarFuncionalidadCommand;
import com.renaser.os.plataforma.application.ports.in.FuncionalidadEnConsola;
import com.renaser.os.plataforma.domain.model.auditoria.CambioAuditado;
import com.renaser.os.plataforma.domain.model.auditoria.TipoDeCambio;
import com.renaser.os.plataforma.domain.model.estado.ReglaDeDisponibilidad;
import com.renaser.os.shared.application.EstadoAutomaticoDeFuncionalidades;
import com.renaser.os.shared.domain.ConsolaBloqueadaException;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.ReautenticacionRequeridaException;
import com.renaser.os.shared.domain.UserId;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La consola de interruptores (contrato §6): el orden de los cerrojos y lo que deja un cambio. Sin Spring. */
@SuppressWarnings("unchecked")
class ConsolaDeFuncionalidadesServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-13T03:30:00Z"));
    private static final String SESION = "sesion-1";

    private final UserId soporte = UserId.of(UUID.randomUUID());
    private final UserId admin = UserId.of(UUID.randomUUID());
    private final Dobles.Base base = new Dobles.Base();
    private final Dobles.Auditoria auditoria = new Dobles.Auditoria();
    private final Dobles.Tokens tokens = new Dobles.Tokens();
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private ConsolaDeFuncionalidadesService service;
    private String tokenDeConsola;

    @BeforeEach
    void armar() {
        when(usuarios.findById(soporte)).thenReturn(Optional.of(
                new UserSummary(soporte, "Dueño", null, UserRole.IT_SUPPORT, UserStatus.ACTIVE)));
        when(usuarios.findById(admin)).thenReturn(Optional.of(
                new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        when(usuarios.findByIds(anyCollection())).thenReturn(Map.of(soporte,
                new UserSummary(soporte, "Dueño", null, UserRole.IT_SUPPORT, UserStatus.ACTIVE)));
        ObjectProvider<EstadoAutomaticoDeFuncionalidades> sinAutomaticos = mock(ObjectProvider.class);
        when(sinAutomaticos.orderedStream()).thenReturn(Stream.empty());
        ObjectProvider<ReglaDeDisponibilidad> sinReglas = mock(ObjectProvider.class);
        when(sinReglas.orderedStream()).thenReturn(Stream.empty());
        EstadoVigente estado = new EstadoVigente(new CopiaDeEstados(base), sinAutomaticos, sinReglas);
        GuardiaDeSoporteTi guardia = new GuardiaDeSoporteTi(usuarios, tokens, Duration.ofMinutes(15));
        service = new ConsolaDeFuncionalidadesService(guardia, new FuncionalidadesEnConsola(estado, base, usuarios),
                estado, base, auditoria, tokens, events, UUID::randomUUID, CLOCK);
        tokenDeConsola = tokens.emitirDeConsola(soporte, SESION, Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("sin el token de la consola: 423, aunque sea Soporte TI")
    void sinTokenEstaBloqueada() {
        assertThatThrownBy(() -> service.listar(new AccesoDeConsola(soporte, null, SESION)))
                .isInstanceOf(ConsolaBloqueadaException.class);
        assertThatThrownBy(() -> service.listar(new AccesoDeConsola(soporte, tokenDeConsola, "otra-sesion")))
                .as("el token vale solo en la sesión que lo pidió").isInstanceOf(ConsolaBloqueadaException.class);
    }

    @Test
    @DisplayName("un ADMIN no usa la consola: 403 antes de mirar el token")
    void adminNoUsaLaConsola() {
        String suyo = tokens.emitirDeConsola(admin, SESION, Duration.ofMinutes(15));

        assertThatThrownBy(() -> service.listar(new AccesoDeConsola(admin, suyo, SESION)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("lista el catálogo entero con su estado: sin filas, todo prendido, versión 0 y sin autor")
    void listaElCatalogo() {
        var lista = service.listar(acceso());

        assertThat(lista).hasSize(52);
        FuncionalidadEnConsola chatGrupos = lista.stream().filter(f -> f.clave().equals("chat.grupos")).findFirst()
                .orElseThrow();
        assertThat(chatGrupos.madre()).isEqualTo("chat");
        assertThat(chatGrupos.prendidaGlobal()).isTrue();
        assertThat(chatGrupos.porRol()).hasSize(UserRole.values().length).doesNotContainValue(false);
        assertThat(chatGrupos.version()).isZero();
        assertThat(chatGrupos.actualizadoPor()).isNull();
    }

    @Test
    @DisplayName("apagar chat.grupos para APRENDIZ: se guarda, se audita con antes y después, y sale el evento")
    void apagarParaUnRol() {
        FuncionalidadEnConsola resultado = service.cambiar(comando("chat.grupos", true,
                Map.of("TRAINEE", false, "MENTOR", true), "Volvemos a las 18:00", 0L, null));

        assertThat(resultado.porRol()).containsEntry(UserRole.TRAINEE, false).containsEntry(UserRole.MENTOR, true);
        assertThat(resultado.aviso()).isEqualTo("Volvemos a las 18:00");
        assertThat(resultado.version()).isEqualTo(1);
        assertThat(resultado.actualizadoPor().nombre()).isEqualTo("Dueño");
        CambioAuditado fila = auditoria.filas.getFirst();
        assertThat(fila.tipo()).isEqualTo(TipoDeCambio.MANUAL);
        assertThat(fila.actor()).isEqualTo(soporte);
        assertThat(fila.motivo()).isEqualTo("Prueba de la consola");
        assertThat(fila.dispositivo()).isEqualTo("JUnit");
        assertThat(fila.antes()).containsEntry("prendidaGlobal", true).containsEntry("apagadaPara", java.util.List.of());
        assertThat(fila.despues()).containsEntry("apagadaPara", java.util.List.of("TRAINEE"));
        ArgumentCaptor<FuncionalidadCambiadaEvent> evento = ArgumentCaptor.forClass(FuncionalidadCambiadaEvent.class);
        verify(events).publishEvent(evento.capture());
        assertThat(evento.getValue().critica()).isFalse();
        assertThat(evento.getValue().apagadaParaTodos()).isFalse();
        assertThat(evento.getValue().resumen()).isEqualTo("Apagada para Aprendiz");
        assertThat(evento.getValue().nombre()).isEqualTo("Chat de grupo");
    }

    @Test
    @DisplayName("un cambio crítico sin reautenticación: 428 y no se guarda ni se audita nada")
    void criticoSinReautenticacion() {
        assertThatThrownBy(() -> service.cambiar(comando("chat", false, Map.of(), null, 0L, null)))
                .isInstanceOf(ReautenticacionRequeridaException.class);
        assertThatThrownBy(() -> service.cambiar(comando("chat.grupos", false, Map.of(), null, 0L, "inventado")))
                .as("apagar para todos también es crítico").isInstanceOf(ReautenticacionRequeridaException.class);

        assertThat(base.filas).isEmpty();
        assertThat(auditoria.filas).isEmpty();
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("un cambio crítico con su reautenticación pasa, y el token sirve una sola vez")
    void criticoConReautenticacion() {
        String reautenticacion = tokens.emitirDeReautenticacion(soporte, SESION, Duration.ofMinutes(5));

        service.cambiar(comando("chat", false, Map.of(), "Chat en mantenimiento", 0L, reautenticacion));

        assertThat(base.filas.get("chat").prendidaGlobal()).isFalse();
        assertThatThrownBy(() -> service.cambiar(comando("chat", true, Map.of(), null, 1L, reautenticacion)))
                .isInstanceOf(ReautenticacionRequeridaException.class);
    }

    @Test
    @DisplayName("con una versión vieja: 409 y no se guarda nada (FR-019)")
    void versionVieja() {
        service.cambiar(comando("chat.grupos", true, Map.of("TRAINEE", false), null, 0L, null));

        assertThatThrownBy(() -> service.cambiar(comando("chat.grupos", true, Map.of(), null, 0L, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(base.filas.get("chat.grupos").apagadaPara()).containsExactly(UserRole.TRAINEE);
    }

    @Test
    @DisplayName("una clave fuera del catálogo (anti-bloqueo), un rol que no existe o sin motivo: 400")
    void pedidosInvalidos() {
        assertThatThrownBy(() -> service.cambiar(comando("login", true, Map.of(), null, 0L, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no tiene interruptor");
        assertThatThrownBy(() -> service.cambiar(comando("chat.grupos", true, Map.of("GUIA", false), null, 0L, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("GUIA");
        assertThatThrownBy(() -> service.cambiar(new CambiarFuncionalidadCommand(acceso(), "chat.grupos", true,
                Map.of(), null, " ", 0L, null, null))).isInstanceOf(IllegalArgumentException.class);
        assertThat(base.filas).isEmpty();
    }

    private AccesoDeConsola acceso() {
        return new AccesoDeConsola(soporte, tokenDeConsola, SESION);
    }

    private CambiarFuncionalidadCommand comando(String clave, boolean prendida, Map<String, Boolean> porRol,
                                                String aviso, long version, String reautenticacion) {
        return new CambiarFuncionalidadCommand(acceso(), clave, prendida, porRol, aviso, "Prueba de la consola",
                version, reautenticacion, "JUnit");
    }
}
