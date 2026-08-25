package com.renaser.os.academy.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Set;

/**
 * Contrato entre modulos: quien tiene acceso vigente a un curso. Lo implementa
 * `academy` (unico dueno de `asignaciones_curso`, con su regla de vigencia
 * desde/hasta/revocada) y lo consume `calendar` para resolver la audiencia
 * `CURSO` de un evento sin duplicar esa regla.
 */
public interface AccesoCursoFinder {

    /** Usuarios con asignacion vigente al curso (directa o por grupo). */
    Set<UserId> usuariosConAcceso(String cursoId);

    /** Atajo para el caso de una sola comprobacion. */
    boolean tieneAcceso(UserId usuarioId, String cursoId);
}
