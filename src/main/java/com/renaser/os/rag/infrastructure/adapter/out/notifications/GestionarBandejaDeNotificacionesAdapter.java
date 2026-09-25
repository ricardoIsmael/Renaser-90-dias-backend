package com.renaser.os.rag.infrastructure.adapter.out.notifications;

import com.renaser.os.rag.api.BandejaDeNotificaciones;
import com.renaser.os.rag.application.ports.out.notificaciones.GestionarBandejaDeNotificacionesPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/**
 * Implementa {@link GestionarBandejaDeNotificacionesPort} delegando en {@link BandejaDeNotificaciones}
 * (D-41): {@code rag} nunca lee la tabla de notificaciones. El contrato vive en {@code rag.api} y lo
 * implementa {@code notifications}, para no crear un ciclo entre los dos modulos (ver el javadoc del
 * contrato). Es una traduccion y nada mas.
 */
@Component
class GestionarBandejaDeNotificacionesAdapter implements GestionarBandejaDeNotificacionesPort {

    private final BandejaDeNotificaciones bandeja;

    GestionarBandejaDeNotificacionesAdapter(BandejaDeNotificaciones bandeja) {
        this.bandeja = bandeja;
    }

    @Override
    public BandejaSinLeer sinLeer(UserId usuarioId, int cuantasRecientes) {
        BandejaDeNotificaciones.ResumenDeBandeja resumen = bandeja.noLeidas(usuarioId, cuantasRecientes);
        return new BandejaSinLeer(resumen.totalNoLeidas(), resumen.recientes().stream()
                .map(aviso -> new AvisoSinLeer(aviso.titulo(), aviso.cuerpo(), aviso.creadaEn()))
                .toList());
    }

    @Override
    public int marcarTodasLeidas(UserId usuarioId) {
        return bandeja.marcarTodasLeidas(usuarioId);
    }
}
