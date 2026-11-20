package com.renaser.os.mentoring.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Un aprendiz del grupo de un mentor está en rojo hoy, según el semáforo del día (spec 057, FR-031). Lo publica el barrido de las 19:00 de la zona
 * del mentor; {@code notifications} lo convierte en el aviso con los botones «Escribirle» y «Ver ficha».
 *
 * <p>Es un evento y no una llamada a {@code notifications} por las mismas dos razones de {@link AvisoDeAcompanamientoEvent}: la dirección de la
 * dependencia y el aislamiento (el outbox corre al listener en su propia transacción después del commit).
 *
 * @param claveDeduplicacion un aviso por mentor, aprendiz y semana: es el {@code origenEventoId}, que tiene índice único (FR-032)
 * @param porcentaje         lo que lleva hoy el aprendiz de sus hábitos y objetivos, entero de 0 a 100
 * @param nombreDelAprendiz  para el título del aviso; como en el aviso de acompañamiento, el push nombra a la persona y el detalle se queda
 *                           dentro de la app, contra un endpoint que revalida permisos
 */
public record RojoDelDiaEvent(UUID claveDeduplicacion, UUID mentorId, UUID aprendizId, UUID grupoId, String nombreDelAprendiz,
                              int porcentaje, Instant detectadoEn) {

    /** Ruta profunda a la ficha del aprendiz, la misma que ya abre la app. El cliente revalida permisos al abrirla. */
    public String rutaApp() {
        return "/mentor/groups/" + grupoId + "/learners/" + aprendizId;
    }
}
