package com.renaser.os.community.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A este grupo se le acaba el periodo y el administrador tiene que hacer algo.
 *
 * <p>Va por evento y no llamando a `notifications` a mano, por el mismo motivo que
 * {@code AvisoDeAcompanamientoEvent}: `community` no tiene por que saber que existe una bandeja
 * de notificaciones. Publica que un grupo esta por vencer; quien quiera enterarse se suscribe.
 * El outbox de Modulith se encarga de que el aviso no se pierda si `notifications` esta caido.
 *
 * @param claveDeduplicacion identifica el EPISODIO —el grupo y su fecha de cierre—, no la
 *                           deteccion. Se usa como {@code origenEventoId}, que tiene indice
 *                           unico: el barrido corre todos los dias de la ventana y el
 *                           administrador recibe UN aviso.
 * @param diasRestantes      contando hoy. El ultimo dia vale 1, no 0.
 */
public record GrupoPorVencerEvent(UUID claveDeduplicacion, UUID celulaId, String nombreDelGrupo,
                                   LocalDate finDelPeriodo, long diasRestantes, Instant detectadoEn) {

    /** Ruta profunda al grupo en el panel. El cliente revalida permisos al abrirla. */
    public String rutaApp() {
        return "/admin/cells/" + celulaId;
    }
}
