package com.renaser.os.mentoring.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Una obligación de hábito de un día, con lo que la respalda.
 *
 * @param estadoHabito   PENDIENTE / EN_CURSO / COMPLETADO / FALLIDO / EXPIRADO.
 * @param entrega        NO_REQUERIDA / SIN_ENTREGA / ENTREGADA. Separado del hábito porque cumplir y entregar son dos
 *                       hechos distintos.
 * @param revision       null cuando no hay entrega. Nunca se convierte en "rechazada".
 * @param evidenciaId    la PRIMERA evidencia (la que acredita el cumplimiento); null si no subió ninguna
 * @param publicacionId  E-628: en el post diario, la PRIMERA publicación del Muro de ese día (la que lo cerró); su
 *                       entrega es ENTREGADA a la hora de esa publicación. null en todo otro hábito, y en el post
 *                       diario de un día sin publicar.
 * @param respuestaTexto spec 005: lo que la persona escribió al cumplirlo («¿Qué sentiste?», el resumen de la
 *                       Pastilla); null si no escribió nada
 * @param evidencias     spec 005: cada archivo o texto subido para esta obligación, de la más vieja a la más nueva.
 *                       Vacía si no subió ninguno. Cada una se abre por {@code GET /evidence/{id}}
 */
public record ObligacionDelDia(UUID registroId, String titulo, String estadoHabito, boolean requiereEvidencia,
                               String entrega, Instant entregadaEn, String revision, UUID evidenciaId,
                               UUID publicacionId, String respuestaTexto, List<EvidenciaDeObligacion> evidencias) {

    public ObligacionDelDia {
        evidencias = evidencias == null ? List.of() : List.copyOf(evidencias);
    }
}
