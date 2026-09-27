package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.calendar.api.RecordatorioEventoDebidoEvent;
import com.renaser.os.notifications.application.ports.out.notificacion.LoadNotificacionPort;
import com.renaser.os.notifications.application.ports.out.notificacion.SaveNotificacionPort;
import com.renaser.os.notifications.application.ports.out.preferencia.LoadPreferenciasPort;
import com.renaser.os.notifications.application.ports.out.push.DesactivarTokenPushPort;
import com.renaser.os.notifications.application.ports.out.push.PushPort;
import com.renaser.os.notifications.application.ports.out.tokenpush.LoadTokenPushPort;
import com.renaser.os.notifications.application.services.AlarmaLocalService;
import com.renaser.os.notifications.application.services.NotificacionService;
import com.renaser.os.notifications.application.services.NotificacionServiceDePrueba;
import com.renaser.os.notifications.domain.model.notificacion.Notificacion;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPushId;
import com.renaser.os.shared.domain.FixedClock;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E-301 / D-182: los recordatorios de eventos llegan, una vez, y respetan preferencias y
 * suspensiones. El listener corre contra el {@link NotificacionService} REAL (sus puertos de salida
 * son una bandeja en memoria), porque lo que se prueba es la cadena completa: evento → fila →
 * push, y la deduplicacion ante una reentrega del outbox.
 *
 * <p>Contra el codigo anterior al 2026-09-26 esta clase no compila: no existia ningun consumidor de
 * {@link RecordatorioEventoDebidoEvent}, que es justamente el bug.
 *
 * <p>El reloj esta a las 00:20 UTC (19:20 del dia anterior en Lima), regla 03.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecordatorioEventoNotificationListenerTest {

    private static final Instant AHORA = Instant.parse("2026-09-29T00:20:00Z");      // 19:20 del 28 en Lima
    private static final Instant INICIO = Instant.parse("2026-09-29T00:30:00Z");     // 19:30 del 28 en Lima
    private static final UUID EVENTO = UUID.randomUUID();

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
    private DesactivarTokenPushPort desactivarTokenPushPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private PlatformTransactionManager transactionManager;

    private final List<Notificacion> bandeja = new ArrayList<>();
    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private RecordatorioEventoNotificationListener listener;

    @BeforeEach
    void setUp() {
        FixedClock clock = FixedClock.at(AHORA);
        NotificacionService service = NotificacionServiceDePrueba.con(new NotificacionServiceDePrueba.Puertos(
                loadNotificacionPort, saveNotificacionPort, loadPreferenciasPort, loadTokenPushPort, pushPort,
                desactivarTokenPushPort, userSummaryFinder, transactionManager), clock);
        listener = new RecordatorioEventoNotificationListener(service, new AlarmaLocalService(loadTokenPushPort), clock);

        when(loadNotificacionPort.existePorOrigen(any(), any(), any())).thenAnswer(inv -> bandeja.stream()
                .anyMatch(n -> n.usuarioId().equals(inv.getArgument(0)) && n.tipo() == inv.getArgument(1)
                        && inv.getArgument(2) != null && inv.getArgument(2).equals(n.origenEventoId())));
        when(saveNotificacionPort.guardar(any())).thenAnswer(inv -> {
            Notificacion n = inv.getArgument(0);
            Notificacion guardada = Notificacion.rehydrate((long) bandeja.size() + 1, n.usuarioId(), n.tipo(),
                    n.titulo(), n.cuerpo(), n.rutaApp(), null, n.creadoEn(), n.origenEventoId());
            bandeja.add(guardada);
            return guardada;
        });
        when(loadPreferenciasPort.habilitadaPara(any(), any())).thenReturn(Optional.empty());
        when(loadTokenPushPort.tokensDe(aprendiz)).thenReturn(List.of(TokenPush.registrar(
                TokenPushId.of(UUID.randomUUID()), aprendiz, "ExponentPushToken[x]", PlataformaPush.ANDROID, clock)));
        when(pushPort.enviar(anyList(), any(), any(), any())).thenReturn(List.of());
        cuentaConEstado(UserStatus.ACTIVE);
    }

    private void cuentaConEstado(UserStatus estado) {
        when(userSummaryFinder.findById(aprendiz)).thenReturn(Optional.of(
                new UserSummary(aprendiz, "Ana", null, UserRole.TRAINEE, estado)));
    }

    private static RecordatorioEventoDebidoEvent recordatorio(long id, UserId destinatario) {
        return conAsistencia(id, destinatario, false);
    }

    private static RecordatorioEventoDebidoEvent conAsistencia(long id, UserId destinatario, Boolean dijoVoy) {
        return new RecordatorioEventoDebidoEvent(id, EVENTO, destinatario, INICIO, "Mentoria", false, dijoVoy,
                "America/Lima", AHORA);
    }

    private void tokensDelAprendiz(PlataformaPush... plataformas) {
        FixedClock clock = FixedClock.at(AHORA);
        List<TokenPush> tokens = new ArrayList<>();
        for (PlataformaPush plataforma : plataformas) {
            tokens.add(TokenPush.registrar(TokenPushId.of(UUID.randomUUID()), aprendiz,
                    "token-" + plataforma + "-" + UUID.randomUUID(), plataforma, clock));
        }
        when(loadTokenPushPort.tokensDe(aprendiz)).thenReturn(tokens);
    }

    @Test
    @DisplayName("un recordatorio -> una fila RECORDATORIO_EVENTO con ruta /eventos/{id} y un push")
    void unRecordatorioUnaNotificacion() {
        listener.on(recordatorio(10L, aprendiz));

        assertThat(bandeja).hasSize(1);
        Notificacion fila = bandeja.getFirst();
        assertThat(fila.tipo()).isEqualTo(TipoNotificacion.RECORDATORIO_EVENTO);
        assertThat(fila.rutaApp()).isEqualTo("/eventos/" + EVENTO);
        assertThat(fila.titulo()).isEqualTo("Mentoria");
        assertThat(fila.cuerpo()).isEqualTo("Empieza en 10 min, a las 19:30.");
        verify(pushPort, times(1)).enviar(anyList(), any(), any(), any());
    }

    @Test
    @DisplayName("el outbox reentrega el mismo evento -> sigue habiendo UNA fila y UN push")
    void reentregaNoDuplica() {
        listener.on(recordatorio(10L, aprendiz));
        listener.on(recordatorio(10L, aprendiz));

        assertThat(bandeja).hasSize(1);
        verify(pushPort, times(1)).enviar(anyList(), any(), any(), any());
    }

    @Test
    @DisplayName("dos filas distintas de la cola (10 min antes y 04:50) -> dos notificaciones")
    void dosRecordatoriosDosNotificaciones() {
        listener.on(recordatorio(10L, aprendiz));
        listener.on(recordatorio(11L, aprendiz));

        assertThat(bandeja).hasSize(2);
    }

    @Test
    @DisplayName("la persona apago RECORDATORIO_EVENTO -> ni fila ni push")
    void preferenciaApagada() {
        when(loadPreferenciasPort.habilitadaPara(aprendiz, TipoNotificacion.RECORDATORIO_EVENTO))
                .thenReturn(Optional.of(false));

        listener.on(recordatorio(10L, aprendiz));

        assertThat(bandeja).isEmpty();
        verify(pushPort, never()).enviar(anyList(), any(), any(), any());
    }

    @Test
    @DisplayName("cuenta suspendida -> la fila queda en su bandeja, el push no sale (E-38)")
    void suspendidoSinPush() {
        cuentaConEstado(UserStatus.SUSPENDED);

        listener.on(recordatorio(10L, aprendiz));

        assertThat(bandeja).hasSize(1);
        verify(pushPort, never()).enviar(anyList(), any(), any(), any());
    }

    @Test
    @DisplayName("un reintento que llega con la ocurrencia ya empezada no avisa nada")
    void reintentoTardioSeDescarta() {
        var tardio = new RecordatorioEventoDebidoEvent(10L, EVENTO, aprendiz, AHORA.minusSeconds(60), "Mentoria",
                false, false, "America/Lima", AHORA.minusSeconds(3600));

        listener.on(tardio);

        assertThat(bandeja).isEmpty();
    }

    /**
     * D-189: la app del telefono programa una alarma local al responder "Voy"; un aviso del
     * servidor encima seria doble. Antes esto lo resolvia {@code calendar} apagando los avisos al
     * confirmar, sin mirar si habia telefono.
     */
    @Test
    @DisplayName("D-189: dijo Voy y tiene telefono (Android) -> ni fila ni push: lo cubre la alarma local")
    void voyConTelefonoNoSeEnvia() {
        listener.on(conAsistencia(10L, aprendiz, true));

        assertThat(bandeja).isEmpty();
        verify(pushPort, never()).enviar(anyList(), any(), any(), any());
    }

    @Test
    @DisplayName("D-189: dijo Voy con iOS y web -> alcanza un telefono para no enviar")
    void voyConIosYWebNoSeEnvia() {
        tokensDelAprendiz(PlataformaPush.WEB, PlataformaPush.IOS);

        listener.on(conAsistencia(10L, aprendiz, true));

        assertThat(bandeja).isEmpty();
    }

    /** El bug de D-189: quien respondia "Voy" desde la web se quedaba sin ningun recordatorio. */
    @Test
    @DisplayName("D-189: dijo Voy pero solo tiene web -> el recordatorio sale (fila y push)")
    void voySoloWebSigueRecibiendo() {
        tokensDelAprendiz(PlataformaPush.WEB);

        listener.on(conAsistencia(10L, aprendiz, true));

        assertThat(bandeja).hasSize(1);
        verify(pushPort, times(1)).enviar(anyList(), any(), any(), any());
    }

    @Test
    @DisplayName("D-189: dijo Voy y no tiene ningun token -> la fila queda en su bandeja")
    void voySinTokensSigueRecibiendo() {
        tokensDelAprendiz();

        listener.on(conAsistencia(10L, aprendiz, true));

        assertThat(bandeja).hasSize(1);
    }

    @Test
    @DisplayName("D-189: publicacion vieja del outbox sin el campo (null) -> se entrega, lado seguro")
    void publicacionViejaSinCampoSeEntrega() {
        listener.on(conAsistencia(10L, aprendiz, null));

        assertThat(bandeja).hasSize(1);
    }

    /**
     * Sin {@code @ApplicationModuleListener} Modulith no guarda la publicacion en el outbox junto
     * con el {@code enviado_en} de la fila, y un fallo aca se perderia sin reintento (D-182).
     */
    @Test
    @DisplayName("el metodo es @ApplicationModuleListener: la entrega queda en el outbox y se reintenta")
    void escuchaComoListenerDeModulo() throws NoSuchMethodException {
        Method on = RecordatorioEventoNotificationListener.class.getDeclaredMethod("on",
                RecordatorioEventoDebidoEvent.class);

        assertThat(on.isAnnotationPresent(ApplicationModuleListener.class)).isTrue();
    }
}
