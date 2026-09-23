package com.renaser.os.rag.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

/**
 * La bandeja de notificaciones de una persona, tal como la lee y la vacia el acompanante
 * (2026-09-23, herramientas {@code consultar_notificaciones} y
 * {@code proponer_marcar_notificaciones_leidas}).
 *
 * <p><b>Por que vive en {@code rag.api} y no en {@code notifications.api}.</b> {@code notifications}
 * ya depende de {@code rag} (escucha {@link PatronDeMalestarRepetidoEvent}). Si {@code rag}
 * importara un contrato de {@code notifications} habria un ciclo entre modulos, y
 * {@code ApplicationModules.verify()} rompe el build. Es la misma inversion que
 * {@code points.api.NotificacionesNoLeidasFinder}: el modulo que consume declara el contrato y
 * {@code notifications} lo implementa.
 *
 * <p><b>Delega, no reimplementa.</b> La implementacion corre los mismos casos de uso que
 * {@code GET /api/v1/notifications} y {@code PUT /api/v1/notifications/read-all}: el guard de cuenta activa,
 * la ventana de retencion y la bandeja propia (nunca la de otro) se deciden en {@code notifications}.
 *
 * <p>Propaga lo que propagan esos casos de uso: {@code NoSuchElementException} si la persona no
 * existe y {@code NotAuthorizedException} si esta suspendida.
 */
public interface BandejaDeNotificaciones {

    /**
     * @param cuantasRecientes cuantas de las no leidas devolver con su texto, las mas nuevas primero
     */
    ResumenDeBandeja noLeidas(UserId usuarioId, int cuantasRecientes);

    /** Mismo efecto que {@code PUT /api/v1/notifications/read-all}. Idempotente. */
    int marcarTodasLeidas(UserId usuarioId);

    /**
     * @param totalNoLeidas el mismo conteo del badge de {@code GET /home} (sin tope de paginacion)
     * @param recientes     las no leidas mas nuevas, a lo sumo las pedidas
     */
    record ResumenDeBandeja(long totalNoLeidas, List<NotificacionNoLeida> recientes) {

        public ResumenDeBandeja {
            recientes = List.copyOf(recientes);
        }
    }

    record NotificacionNoLeida(String titulo, String cuerpo, Instant creadaEn) {
    }
}
