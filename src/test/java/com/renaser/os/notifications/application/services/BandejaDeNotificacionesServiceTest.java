package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.notificacion.ListarNotificacionesUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.MarcarTodasLeidasUseCase;
import com.renaser.os.notifications.domain.model.notificacion.Notificacion;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.points.api.NotificacionesNoLeidasFinder;
import com.renaser.os.rag.api.BandejaDeNotificaciones.NotificacionNoLeida;
import com.renaser.os.rag.api.BandejaDeNotificaciones.ResumenDeBandeja;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** La bandeja que lee el acompanante: las no leidas mas nuevas y el mismo total del badge. */
class BandejaDeNotificacionesServiceTest {

    private static final UserId PERSONA = UserId.of(UUID.randomUUID());
    private static final Instant T0 = Instant.parse("2026-09-23T12:00:00Z");

    private final ListarNotificacionesUseCase listar = mock(ListarNotificacionesUseCase.class);
    private final MarcarTodasLeidasUseCase marcarTodas = mock(MarcarTodasLeidasUseCase.class);
    private final NotificacionesNoLeidasFinder noLeidas = mock(NotificacionesNoLeidasFinder.class);
    private final BandejaDeNotificacionesService service =
            new BandejaDeNotificacionesService(listar, marcarTodas, noLeidas);

    private static Notificacion notificacion(long id, String titulo, Instant leidaEn, Instant creada) {
        return Notificacion.rehydrate(id, PERSONA, TipoNotificacion.HITO_PROGRAMA, titulo, "cuerpo " + id, null,
                leidaEn, creada);
    }

    @Test
    @DisplayName("salta las leidas, respeta el tope pedido y toma el total del badge, no de la bandeja topada")
    void noLeidas() {
        when(listar.listar(PERSONA)).thenReturn(List.of(notificacion(3, "tres", null, T0),
                notificacion(2, "dos", T0, T0.minusSeconds(60)), notificacion(1, "uno", null, T0.minusSeconds(120)),
                notificacion(0, "cero", null, T0.minusSeconds(180))));
        when(noLeidas.contarNoLeidas(PERSONA)).thenReturn(250L);

        ResumenDeBandeja resumen = service.noLeidas(PERSONA, 2);

        assertThat(resumen.totalNoLeidas()).isEqualTo(250L);
        assertThat(resumen.recientes()).containsExactly(new NotificacionNoLeida("tres", "cuerpo 3", T0),
                new NotificacionNoLeida("uno", "cuerpo 1", T0.minusSeconds(120)));
    }

    @Test
    @DisplayName("marcar todas delega en MarcarTodasLeidasUseCase")
    void marcar() {
        when(marcarTodas.marcarTodas(PERSONA)).thenReturn(3);

        assertThat(service.marcarTodasLeidas(PERSONA)).isEqualTo(3);
    }
}
