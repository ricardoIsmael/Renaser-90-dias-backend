package com.renaser.os.points.api;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Todo lo que se muestra del semáforo de UNA persona: su ventana vigente, su pausa si la tiene y
 * sus últimas semanas cerradas. Es la respuesta de {@code GET /api/v1/me/semaforo} y la misma que
 * ven el mentor y el administrador de esa persona (docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §4.1).
 *
 * @param aplica      false si la persona no tiene programa activado: el resto viene vacío
 * @param obligatorio true para el aprendiz (no puede pausarlo); false para el staff con programa propio
 * @param semanas     las últimas semanas cerradas, de la más vieja a la más nueva
 * @param calculadoEn cuándo el barrido escribió por última vez algo de esta persona; null si nunca
 */
public record DetalleDelSemaforo(boolean aplica, boolean obligatorio, ZoneId zona, PausaDelSemaforo pausa,
                                 VentanaDelSemaforo vigente, List<SemanaCerrada> semanas, Instant calculadoEn) {

    public DetalleDelSemaforo {
        semanas = semanas == null ? List.of() : List.copyOf(semanas);
    }

    public static DetalleDelSemaforo noAplica(ZoneId zona, boolean obligatorio) {
        return new DetalleDelSemaforo(false, obligatorio, zona, null, null, List.of(), null);
    }
}
