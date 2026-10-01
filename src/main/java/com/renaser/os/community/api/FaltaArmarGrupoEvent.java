package com.renaser.os.community.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Al staff le toca armar algo para que la gente tenga grupo (D-240). Dos casos, un solo evento:
 *
 * <ul>
 *   <li>{@link Motivo#SIN_GRUPO_EN_CURSO}: alguien terminó la bienvenida y la cohorte no tiene
 *       ningún grupo en curso. Va al administrador y al líder de mentores.</li>
 *   <li>{@link Motivo#GRUPO_SIN_MENTOR}: un grupo está en curso sin mentor. Va al líder de
 *       mentores.</li>
 * </ul>
 *
 * <p>Por evento y no llamando a {@code notifications}, igual que {@link GrupoPorVencerEvent}:
 * {@code community} no sabe que existe una bandeja.
 *
 * @param claveDeduplicacion una por episodio y día local ({@code AvisoDeArmadoDeGrupos}); viaja como
 *                           {@code origenEventoId}, que tiene índice único
 * @param celulaId           el grupo sin mentor; null en {@code SIN_GRUPO_EN_CURSO}
 * @param titulo             el título de la bandeja
 * @param cuerpo             el texto que lee el staff, ya armado
 */
public record FaltaArmarGrupoEvent(UUID claveDeduplicacion, Motivo motivo, UUID cohorteId, UUID celulaId,
                                   String titulo, String cuerpo, Instant detectadoEn) {

    public enum Motivo {
        SIN_GRUPO_EN_CURSO,
        GRUPO_SIN_MENTOR
    }

    /** Ruta profunda en el panel: el grupo si hay uno, la lista si falta crearlo. */
    public String rutaApp() {
        return celulaId == null ? "/admin/cells" : "/admin/cells/" + celulaId;
    }
}
