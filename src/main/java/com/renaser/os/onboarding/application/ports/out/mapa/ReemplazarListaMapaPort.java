package com.renaser.os.onboarding.application.ports.out.mapa;

import com.renaser.os.onboarding.domain.model.mapa.AccionesDelMapa;
import com.renaser.os.onboarding.domain.model.mapa.ProtocolosDelMapa;
import com.renaser.os.shared.domain.UserId;

/**
 * Escritura de las dos listas del Mapa. <b>Reemplaza, no acumula</b>: el cliente guarda por PASO
 * —igual que `guardarCapitulo` del onboarding— y manda la lista completa de esa vista, no una fila
 * suelta. Guardar de a una obligaria ademas a inventar un "borrar la que sacaste", que es
 * justamente donde se acumulan las filas fantasma.
 *
 * <p>El {@code habitoId} ya vinculado NO se pierde al reemplazar: el adaptador lo conserva por
 * {@code accionId}, porque perderlo romperia la idempotencia de la activacion (AC-07).
 */
public interface ReemplazarListaMapaPort {

    void reemplazarAcciones(UserId participanteId, AccionesDelMapa acciones);

    void reemplazarProtocolos(UserId participanteId, ProtocolosDelMapa protocolos);
}
