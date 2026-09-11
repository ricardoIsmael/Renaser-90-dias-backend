package com.renaser.os.mentoring.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Se detectó una condición que el mentor debería mirar.
 *
 * <p>Es un evento y no una llamada directa a {@code notifications} por dos razones. La primera
 * es de dirección: {@code notifications} ya escucha eventos de {@code habits}, y que este módulo
 * dependa de él invertiría ese sentido. La segunda es de aislamiento: el outbox de Spring
 * Modulith corre al listener en su propia transacción después del commit, así que un fallo al
 * notificar no puede tumbar el barrido que lo originó.
 *
 * @param claveDeduplicacion identifica el EPISODIO, no la detección. Se usa como
 *                           {@code origenEventoId}, que tiene índice único: repetir el barrido
 *                           mientras la condición siga sin resolverse no crea otra notificación.
 * @param magnitud           días sin actividad o cantidad de evidencias vencidas.
 * @param nombreDelAlumno    para el texto en la bandeja del mentor. El detalle autorizado se
 *                           carga dentro de la app; el push no lleva datos personales.
 */
public record AvisoDeAcompanamientoEvent(UUID claveDeduplicacion, UUID mentorId, UUID alumnoId, UUID grupoId,
                                          String motivo, int magnitud, String nombreDelAlumno,
                                          Instant detectadoEn) {

    /** Ruta profunda al detalle del alumno. El cliente revalida permisos al abrirla. */
    public String rutaApp() {
        return "/mentor/groups/" + grupoId + "/learners/" + alumnoId;
    }
}
