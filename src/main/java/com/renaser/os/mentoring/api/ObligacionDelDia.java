package com.renaser.os.mentoring.api;

import com.renaser.os.habits.api.MedicionDeLaObligacion;

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
 * @param medicion       spec 037: el número del día de un hábito medible (los km) y de dónde salió —la captura o los pasos
 *                       del teléfono, con su fuente y si quedó «por revisar»—; null en un hábito que no mide nada
 */
public record ObligacionDelDia(UUID registroId, String titulo, String estadoHabito, boolean requiereEvidencia,
                               String entrega, Instant entregadaEn, String revision, UUID evidenciaId,
                               UUID publicacionId, String respuestaTexto, List<EvidenciaDeObligacion> evidencias,
                               MedicionDeLaObligacion medicion) {

    public ObligacionDelDia {
        evidencias = evidencias == null ? List.of() : List.copyOf(evidencias);
    }

    /** Sin medición: la forma de los llamadores anteriores a la spec 037. */
    public ObligacionDelDia(UUID registroId, String titulo, String estadoHabito, boolean requiereEvidencia,
                            String entrega, Instant entregadaEn, String revision, UUID evidenciaId, UUID publicacionId,
                            String respuestaTexto, List<EvidenciaDeObligacion> evidencias) {
        this(registroId, titulo, estadoHabito, requiereEvidencia, entrega, entregadaEn, revision, evidenciaId,
                publicacionId, respuestaTexto, evidencias, null);
    }
}
