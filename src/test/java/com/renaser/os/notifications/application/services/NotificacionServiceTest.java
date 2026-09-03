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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

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
    @Mock
    private UserSummaryFinder userSummaryFinder;
    /** No necesita stubbing: TransactionTemplate.execute con getTransaction()==null solo corre
     * el callback directo (mismo criterio que ConversacionServiceTest, C-10). */
    @Mock
    private PlatformTransactionManager transactionManager;

    private NotificacionService service;

    @BeforeEach
    void setUp() {
        service = new NotificacionService(loadNotificacionPort, saveNotificacionPort, loadPreferenciasPort,
                loadTokenPushPort, pushPort, new ActorNotificacionesGuard(userSummaryFinder), CLOCK,
                transactionManager);
        lenient().when(userSummaryFinder.findById(any())).thenAnswer(inv -> Optional.of(
                new UserSummary(inv.getArgument(0), "Test", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
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
                new EmitirNotificacionCommand(usuario, TipoNotificacion.SANTUARIO_ROTO, "T", "C", null, null));

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
                new EmitirNotificacionCommand(usuario, TipoNotificacion.SANTUARIO_ROTO, "T", "C", null, null));

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
                new EmitirNotificacionCommand(usuario, TipoNotificacion.MENSAJE_MENTOR, "T", "C", null, null));

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
                new EmitirNotificacionCommand(usuario, TipoNotificacion.ANUNCIO_SISTEMA, "T", "C", null, null));

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

    @Test
    @DisplayName("E-38: una cuenta SUSPENDIDA no puede leer ni operar su bandeja")
    void actorSuspendidoNoOperaLaBandeja() {
        UserId actor = usuario();
        when(userSummaryFinder.findById(actor)).thenReturn(Optional.of(
                new UserSummary(actor, "Suspendido", null, UserRole.TRAINEE, UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> service.listar(actor)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.marcarLeida(actor, 1L)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.marcarTodas(actor)).isInstanceOf(NotAuthorizedException.class);
        verify(saveNotificacionPort, never()).marcarTodasLeidas(any(), any());
    }

    /**
     * E-38: {@code emitir} es la excepcion deliberada — la invocan los listeners de eventos de
     * otros modulos, no un usuario. Un suspendido debe seguir ACUMULANDO su bandeja (lo que no
     * puede es leerla), y bloquear aca romperia el outbox de Modulith.
     */
    @Test
    @DisplayName("E-38: emitir() ni siquiera consulta el estado del destinatario — un suspendido "
            + "sigue acumulando bandeja (lo que no puede es leerla)")
    void emitirNoVerificaElEstadoDelDestinatario() {
        UserId destinatario = usuario();
        when(loadPreferenciasPort.habilitadaPara(destinatario, TipoNotificacion.MENSAJE_MENTOR))
                .thenReturn(Optional.of(true));

        Optional<Notificacion> emitida = service.emitir(new EmitirNotificacionCommand(destinatario,
                TipoNotificacion.MENSAJE_MENTOR, "Titulo", "Cuerpo", null, null));

        assertThat(emitida).isPresent();
        verify(saveNotificacionPort).guardar(any());
        // La asercion clave: el guard no se invoca en este camino, por eso da igual el estado.
        verify(userSummaryFinder, never()).findById(any());
    }

    /**
     * C-7: el outbox de Spring Modulith es at-least-once — el mismo evento
     * (mismo {@code origenEventoId}) puede llegarle a {@code emitir} mas de una vez (reintento
     * tras un fallo transitorio, o la republicacion al reiniciar). La segunda entrega choca
     * contra {@code notificaciones_origen_evento_uk} (V16); en este test unitario se simula esa
     * colision con el puerto lanzando {@link DataIntegrityViolationException} directamente
     * (la prueba de que el indice real la produce vive en
     * {@code NotificacionEmitirIdempotenciaIT}, con Testcontainers).
     */
    @Test
    @DisplayName("C-7: emitir() con el mismo origenEventoId dos veces -> la segunda entrega no "
            + "crea una fila nueva ni reenvia el push")
    void emitirConElMismoOrigenEventoDosVecesEsIdempotente() {
        UserId usuario = usuario();
        UUID origenEventoId = UUID.randomUUID();
        when(loadPreferenciasPort.habilitadaPara(usuario, TipoNotificacion.HITO_PROGRAMA))
                .thenReturn(Optional.of(true));
        // doThrow y no when(...).thenThrow: `when(mock.guardar(any()))` INVOCA el metodo, y setUp ya
        // lo dejo con un thenAnswer — esa invocacion corria la respuesta con argumento nulo y
        // reventaba antes de empezar el test. doThrow no invoca nada.
        doThrow(new DataIntegrityViolationException("notificaciones_origen_evento_uk"))
                .when(saveNotificacionPort).guardar(any());

        Optional<Notificacion> resultado = service.emitir(
                new EmitirNotificacionCommand(usuario, TipoNotificacion.HITO_PROGRAMA, "T", "C", null,
                        origenEventoId));

        assertThat(resultado).isEmpty();
        verify(pushPort, never()).enviar(anyList(), any(), any());
    }
}
