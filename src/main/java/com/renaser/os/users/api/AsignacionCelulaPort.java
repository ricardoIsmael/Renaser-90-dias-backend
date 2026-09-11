package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.UUID;

/**
 * Escritura de {@code participantes_programa.celula_id} para quien SÍ puede validar que la
 * célula exista (gap #25, docs/PLAN_INTEGRACION_FRONTEND.md §5) — hoy solo {@code community},
 * dueño del agregado {@code Celula}. {@code users} es dueño de la columna, no de la validación
 * de existencia: quien llama debe confirmar la célula antes de invocar {@link #asignarCelula}.
 */
public interface AsignacionCelulaPort {

    void asignarCelula(UserId actorId, UserId traineeId, UUID celulaId);

    void quitarCelula(UserId actorId, UserId traineeId);

    /**
     * Sincroniza los DOS punteros de proyeccion desde un proceso del sistema — traslado o
     * rotacion — sin actor humano detras.
     *
     * <p>Existe por dos razones concretas. La primera: {@link #asignarCelula} exige un
     * administrador activo, y un job no tiene ninguno; fabricar un usuario tecnico para
     * satisfacer el guard seria peor que no tenerlo (plan.md §5, "el job no requiere token de
     * usuario falso"). La segunda: hasta ahora no habia forma publica de tocar
     * {@code participantes_programa.mentor_id}, asi que rotar el mentor de una celula dejaba
     * ese puntero apuntando al mentor anterior — y es el que decide a quien se le autoriza la
     * evidencia de ese aprendiz (research.md lo marca como riesgo).
     *
     * <p><b>No lleva autorizacion propia.</b> Quien llama ya la resolvio: o es un comando
     * administrativo que verifico permisos, o es un job disparado por la politica de la
     * cohorte. El puerto no es alcanzable por HTTP.
     *
     * @param mentorId mentor vigente del grupo, o {@code null} si el grupo no tiene (queda
     *                 cubierto por soporte). {@code null} limpia el puntero en vez de dejar
     *                 el anterior, que seria mentira.
     */
    void sincronizarAcompanamiento(UserId traineeId, UUID celulaId, UserId mentorId);
}
