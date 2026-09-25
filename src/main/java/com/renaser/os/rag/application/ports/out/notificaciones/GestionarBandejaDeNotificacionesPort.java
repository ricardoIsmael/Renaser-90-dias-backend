package com.renaser.os.rag.application.ports.out.notificaciones;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

/**
 * Puerto propio de {@code rag} para leer y vaciar la bandeja de notificaciones de la persona
 * (2026-09-23, herramientas {@code consultar_notificaciones} y
 * {@code proponer_marcar_notificaciones_leidas}). El adaptador delega en
 * {@code rag.api.BandejaDeNotificaciones}, que implementa {@code notifications} con los mismos
 * casos de uso que la app.
 *
 * <p>Propaga lo que propaga {@code notifications}: {@code NoSuchElementException} si la persona no
 * existe y {@code NotAuthorizedException} si esta suspendida.
 */
public interface GestionarBandejaDeNotificacionesPort {

    /** @param cuantasRecientes cuantas no leidas traer con su texto, las mas nuevas primero */
    BandejaSinLeer sinLeer(UserId usuarioId, int cuantasRecientes);

    /** @return cuantas se marcaron (0 si no habia ninguna) */
    int marcarTodasLeidas(UserId usuarioId);

    record BandejaSinLeer(long total, List<AvisoSinLeer> recientes) {

        public BandejaSinLeer {
            recientes = List.copyOf(recientes);
        }
    }

    record AvisoSinLeer(String titulo, String cuerpo, Instant creadoEn) {
    }
}
