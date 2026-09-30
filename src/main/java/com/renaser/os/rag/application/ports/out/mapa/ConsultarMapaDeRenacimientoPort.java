package com.renaser.os.rag.application.ports.out.mapa;

import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona;
import com.renaser.os.shared.domain.UserId;

/**
 * El Mapa de Renacimiento (Dia 7) de la persona que conversa (D-233). Lo resuelve {@code onboarding}
 * ({@code MapaDeRenacimientoFinder}); {@code rag} no toca sus tablas (D-41).
 */
public interface ConsultarMapaDeRenacimientoPort {

    /** Nunca {@code null}: quien no lo recorrio sale como {@link MapaDeLaPersona#sinMapa()}. */
    MapaDeLaPersona de(UserId participanteId);
}
