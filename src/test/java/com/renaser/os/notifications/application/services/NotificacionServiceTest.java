package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.application.ports.out.notificacion.LoadNotificacionPort;
import com.renaser.os.notifications.application.ports.out.notificacion.SaveNotificacionPort;
import com.renaser.os.notifications.application.ports.out.preferencia.LoadPreferenciasPort;
import com.renaser.os.notifications.application.ports.out.push.PushPort;
import com.renaser.os.notifications.application.ports.out.tokenpush.LoadTokenPushPort;
import com.renaser.os.notifications.domain.model.notificacion.Notificacion;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private LoadNotificacionPort loadNotificacionPort;
    @Mock
    private SaveNotificacionPort saveNotificacionPort;
    @Mock
    private LoadPreferenciasPort loadPreferenciasPort;
    @Mock
    private LoadTokenPushPort loadTokenPushPort;
    @Mock
    private PushPort pushPort;

    private NotificacionService service;

    @BeforeEach
    void setUp() {
        service = new NotificacionService(loadNotificacionPort, saveNotificacionPort, loadPreferenciasPort,
                loadTokenPushPort, pushPort, CLOCK);
        lenient().when(saveNotificacionPort.guardar(any())).thenAnswer(inv -> {
            Notificacion n = inv.getArgument(0);
            return Notificacion.rehydrate(1L, n.usuarioId(), n.tipo(), n.titulo(), n.cuerpo(), n.rutaApp(),
                    n.leidaEn(), n.creadoEn());
        });
        lenient().when(loadTokenPushPort.tokensDe(any())).thenReturn(List.of());
    }

    private static UserId usuario() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("emitir() sin fila de preferencia -> default habilitada, se crea la notificacion")
    void emitirSinPreferenciaUsaDefaultHabilitada() {
        UserId usuario = usuario();
        when(loadPreferenciasPort.habilitadaPara(usuario, TipoNotificacion.SANTUARIO_ROTO))
                .thenReturn(Optional.empty());

        Optional<Notificacion> resultado = service.emitir(
                new EmitirNotificacionCommand(usuario, TipoNotificacion.SANTUARIO_ROTO, "T", "C", null));

        assertThat(resultado).isPresent();
        verify(saveNotificacionPort).guardar(any());
    }

    @Test
    @DisplayName("emitir() con preferencia EXPLICITAMENTE en false -> no se crea nada")
    void emitirConPreferenciaApagadaNoCreaNada() {
        UserId usuario = usuario();
        when(loadPreferenciasPort.habilitadaPara(usuario, TipoNotificacion.SANTUARIO_ROTO))
                .thenReturn(Optional.of(false));

        Optional<Notificacion> resultado = service.emitir(
                new EmitirNotificacionCommand(usuario, TipoNotificacion.SANTUARIO_ROTO, "T", "C", null));

        assertThat(resultado).isEmpty();
        verify(saveNotificacionPort, never()).guardar(any());
    }

    @Test
    @DisplayName("emitir() con preferencia explicita en true tambien crea (mismo camino que sin fila)")
    void emitirConPreferenciaExplicitaTrueCrea() {
        UserId usuario = usuario();
        when(loadPreferenciasPort.habilitadaPara(usuario, TipoNotificacion.MENSAJE_MENTOR))
                .thenReturn(Optional.of(true));

        Optional<Notificacion> resultado = service.emitir(
                new EmitirNotificacionCommand(usuario, TipoNotificacion.MENSAJE_MENTOR, "T", "C", null));

        assertThat(resultado).isPresent();
    }

    @Test
    @DisplayName("un fallo del push (best-effort) NO tumba la emision de la notificacion")
    void fallaDePushNoTumbaLaEmision() {
        UserId usuario = usuario();
        when(loadPreferenciasPort.habilitadaPara(any(), any())).thenReturn(Optional.empty());
        when(loadTokenPushPort.tokensDe(usuario)).thenReturn(List.of("tok-1"));
        doThrow(new RuntimeException("Expo caido")).when(pushPort).enviar(anyList(), any(), any());

        Optional<Notificacion> resultado = service.emitir(
                new EmitirNotificacionCommand(usuario, TipoNotificacion.ANUNCIO_SISTEMA, "T", "C", null));

        assertThat(resultado).isPresent();
        verify(saveNotificacionPort).guardar(any());
    }

    @Test
    void listarUsaLaVentanaDeRetencionYElLimiteDeLaBandeja() {
        UserId actor = usuario();

        service.listar(actor);

        Instant esperado = CLOCK.now().minus(Notificacion.RETENCION_DIAS, ChronoUnit.DAYS);
        verify(loadNotificacionPort).bandeja(eq(actor), eq(esperado), eq(Notificacion.LIMITE_BANDEJA));
    }

    @Test
    @DisplayName("marcarLeida() es idempotente: 0 filas actualizadas pero SI existe -> exito (no excepcion)")
    void marcarLeidaYaLeidaEsIdempotente() {
        UserId actor = usuario();
        when(saveNotificacionPort.marcarLeida(1L, actor, CLOCK.now())).thenReturn(0);
        when(loadNotificacionPort.existeDe(1L, actor)).thenReturn(true);

        var resultado = service.marcarLeida(actor, 1L);

        assertThat(resultado.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("marcarLeida() de un id ajeno o inexistente -> NoSuchElementException (-> 404), "
            + "sin distinguir cual de los dos casos es (mismo criterio que el repo viejo)")
    void marcarLeidaAjenaOInexistenteLanza404() {
        UserId actor = usuario();
        when(saveNotificacionPort.marcarLeida(99L, actor, CLOCK.now())).thenReturn(0);
        when(loadNotificacionPort.existeDe(99L, actor)).thenReturn(false);

        assertThatThrownBy(() -> service.marcarLeida(actor, 99L)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void marcarTodasDelegaEnElPuertoConLaHoraActual() {
        UserId actor = usuario();
        when(saveNotificacionPort.marcarTodasLeidas(actor, CLOCK.now())).thenReturn(3);

        int actualizadas = service.marcarTodas(actor);

        assertThat(actualizadas).isEqualTo(3);
    }
}
