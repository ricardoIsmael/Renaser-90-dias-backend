package com.renaser.os.mentoring.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cierre semanal del semáforo de un grupo, para su mentor: cuántos de sus aprendices cerraron la
 * semana sábado→viernes en cada color (D-168, docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §1.2).
 *
 * <p>Evento y no llamada directa a {@code notifications}, por los mismos dos motivos que
 * {@link AvisoDeAcompanamientoEvent}: dirección de dependencias y aislamiento del barrido.
 *
 * @param claveDeduplicacion {@code UUID.nameUUIDFromBytes("semaforo-grupo:" + grupoId + ":" + hasta)}
 */
public record ResumenSemanalDelGrupoEvent(UUID claveDeduplicacion, UUID mentorId, UUID grupoId, String grupoNombre,
                                          LocalDate desde, LocalDate hasta, int verde, int amarillo, int rojo,
                                          int sinDatos, int total, Instant generadoEn) {

    /** Ruta profunda a la tabla del grupo. El cliente revalida permisos al abrirla. */
    public String rutaApp() {
        return "/mentor/groups/" + grupoId + "/semaforo";
    }
}
