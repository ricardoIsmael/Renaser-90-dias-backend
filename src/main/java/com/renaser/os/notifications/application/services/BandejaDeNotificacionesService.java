package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.notificacion.ListarNotificacionesUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.MarcarTodasLeidasUseCase;
import com.renaser.os.notifications.domain.model.notificacion.Notificacion;
import com.renaser.os.points.api.NotificacionesNoLeidasFinder;
import com.renaser.os.rag.api.BandejaDeNotificaciones;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementa {@link BandejaDeNotificaciones} (2026-09-23) para el acompanante de {@code rag},
 * componiendo lo que ya sirve a la app: la bandeja de {@link ListarNotificacionesUseCase}, el
 * conteo del badge de {@link NotificacionesNoLeidasFinder} y {@link MarcarTodasLeidasUseCase}.
 * Aca no vive ninguna regla: el guard de cuenta activa y la ventana de retencion se aplican en
 * esos casos de uso.
 *
 * <p>El total sale del conteo del badge y no de contar la bandeja, porque la bandeja esta topada
 * en {@code Notificacion.LIMITE_BANDEJA}: con mas no leidas que eso, el acompanante diria un
 * numero distinto del que ve la persona en la app.
 */
@Service
class BandejaDeNotificacionesService implements BandejaDeNotificaciones {

    private final ListarNotificacionesUseCase listarUseCase;
    private final MarcarTodasLeidasUseCase marcarTodasUseCase;
    private final NotificacionesNoLeidasFinder noLeidasFinder;

    BandejaDeNotificacionesService(ListarNotificacionesUseCase listarUseCase,
                                   MarcarTodasLeidasUseCase marcarTodasUseCase,
                                   NotificacionesNoLeidasFinder noLeidasFinder) {
        this.listarUseCase = listarUseCase;
        this.marcarTodasUseCase = marcarTodasUseCase;
        this.noLeidasFinder = noLeidasFinder;
    }

    @Override
    public ResumenDeBandeja noLeidas(UserId usuarioId, int cuantasRecientes) {
        List<NotificacionNoLeida> recientes = listarUseCase.listar(usuarioId).stream()
                .filter(notificacion -> !notificacion.estaLeida())
                .limit(Math.max(0, cuantasRecientes))
                .map(BandejaDeNotificacionesService::aNoLeida)
                .toList();
        return new ResumenDeBandeja(noLeidasFinder.contarNoLeidas(usuarioId), recientes);
    }

    @Override
    public int marcarTodasLeidas(UserId usuarioId) {
        return marcarTodasUseCase.marcarTodas(usuarioId);
    }

    private static NotificacionNoLeida aNoLeida(Notificacion notificacion) {
        return new NotificacionNoLeida(notificacion.titulo(), notificacion.cuerpo(), notificacion.creadoEn());
    }
}
