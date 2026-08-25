package com.renaser.os.habits.application.ports.out.radar;

import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoadRegistroRadarPort {

    /** Mas reciente primero (indice `radar_perfil_fecha_idx`, baseline linea ~634). */
    Optional<RegistroRadar> ultimoDeParticipante(UserId participanteId);

    /**
     * Pagina de historial ordenada por `creadoEn` descendente. Cursor-based
     * (radar.ts:357-390, `getRadarHistory`): {@code cursor == null} trae la
     * pagina mas reciente; con cursor, solo registros con `creadoEn < cursor`
     * — asi una entrada nueva entre paginas nunca desplaza filas ya cargadas.
     */
    List<RegistroRadar> historialDeParticipante(UserId participanteId, Instant cursor, int tamanoPagina);
}
